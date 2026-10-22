"""LangGraph supervisor + specialist agents for complete bundles (the J stage).

Agents receive the extracted text form only, never PDFs. The supervisor is a rule-based
router node (no model call): it fans out to the document, risk and policy agents in
parallel, re-plans a targeted exclusion search when the risk agent raises a MEDIUM/HIGH
signal, then hands every result to the summary agent.
"""

import json
import operator
from collections.abc import Callable, Sequence
from time import monotonic
from typing import Annotated, Any, TypedDict, TypeVar

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.graph import END, START, StateGraph
from pydantic import Field

from app.core.resources import load_prompt_section
from app.providers.base import AgentRun
from app.rag.store import Hit, Retriever
from app.schemas.jev import Item, RiskSignal, StrictModel

REVIEWER = "① 서류 검토 에이전트"
POLICY = "② 약관 검색 에이전트"
POLICY_RECHECK = "② 약관 재검색 (재계획)"
RETRIEVER = "pgvector 약관 검색"
RISK = "③ 의심 검토 에이전트"
SUMMARY = "④ 요약 에이전트"
FACT_FIELDS = (
    ("diagnosis", "진단명"),
    ("disease_code", "질병분류기호"),
    ("chief_complaint", "주호소"),
    ("treatment_plan", "치료 계획"),
    ("department", "진료과"),
)


class ReviewOut(StrictModel):
    items: list[Item] = Field(min_length=1, max_length=100)


class RiskOut(StrictModel):
    risk_signals: list[RiskSignal] = Field(max_length=20)


class PolicyPick(StrictModel):
    clause_id: str = Field(max_length=64)
    reason: str = Field(min_length=1, max_length=500)


class PolicyOut(StrictModel):
    refs: list[PolicyPick] = Field(max_length=4)


class SummaryOut(StrictModel):
    summary: str = Field(min_length=1, max_length=2000)


class TeamState(TypedDict, total=False):
    form: str
    t0: float
    items: list[Item]
    risk_signals: list[RiskSignal]
    summary: str
    hits: Annotated[list[Hit], operator.add]
    picks: Annotated[list[PolicyPick], operator.add]
    runs: Annotated[list[AgentRun], operator.add]
    replanned: bool


S = TypeVar("S", bound=StrictModel)


# Multi-query retrieval: the care type's coverage and the claim-document rules are always
# worth checking; the case facts add whatever is specific to this claim.
INTENT_QUERIES = {
    "OUTPATIENT_THREE_DOCS": "통원 급여 의료비 보상내용 본인부담금 공제금액 통원 1회당 한도",
    "LEGACY_MEDICAL_THREE_DOCS": "입원 통원 급여 의료비 보상내용 본인부담금 보상한도",
}
CLAIM_QUERY = "보험금의 청구 제출 서류 진료비 계산서 영수증 세부산정내역"


def case_facts(form: str) -> str:
    """Retrieval query: diagnosis and care facts only, never identity fields."""
    data = json.loads(form.removeprefix("JEV_TEXT_FORM="))
    facts = [
        "외래 통원 진료비 청구" if data["bundle"] == "OUTPATIENT_THREE_DOCS" else "진단 진료비 청구"
    ]
    for name, label in FACT_FIELDS:
        values = {
            d["fields"][name]["value"]
            for d in data["documents"]
            if d["fields"].get(name, {}).get("value")
        }
        if values:
            facts.append(f"{label}: {', '.join(sorted(values))}")
    return ". ".join(facts)[:600]


class AgentTeam:
    def __init__(
        self,
        llm: BaseChatModel,
        retriever: Retriever | None,
        top_k: int,
        *,
        clock: Callable[[], float] = monotonic,
    ) -> None:
        self.llm = llm
        self.retriever = retriever
        self.top_k = top_k
        self.clock = clock
        self.prompts = {
            s: load_prompt_section(s) for s in ("REVIEWER", "RISK", "POLICY", "SUMMARY")
        }
        self.graph = self._build()

    def run(self, form: str) -> TeamState:
        initial: TeamState = {
            "form": form,
            "t0": self.clock(),
            "hits": [],
            "picks": [],
            "runs": [],
            "replanned": False,
        }
        return self.graph.invoke(initial)

    # --- graph -----------------------------------------------------------------------------

    def _build(self) -> Any:
        graph = StateGraph(TeamState)
        graph.add_node("supervisor", lambda state: {})
        graph.add_node("reviewer", self._reviewer)
        graph.add_node("risk", self._risk)
        graph.add_node("policy", lambda state: self._policy(state, recheck=False))
        graph.add_node("policy_recheck", lambda state: self._policy(state, recheck=True))
        graph.add_node("summarizer", self._summarizer)
        graph.add_edge(START, "supervisor")
        graph.add_conditional_edges(
            "supervisor",
            self._route,
            ["reviewer", "risk", "policy", "policy_recheck", "summarizer", END],
        )
        for worker in ("reviewer", "risk", "policy", "policy_recheck", "summarizer"):
            graph.add_edge(worker, "supervisor")
        return graph.compile()

    def _route(self, state: TeamState) -> Sequence[str] | str:
        if "items" not in state:
            return ["reviewer", "risk", "policy"]  # independent specialists run in parallel
        if (
            self.retriever is not None
            and not state["replanned"]
            and any(s.level in {"MEDIUM", "HIGH"} for s in state.get("risk_signals", []))
        ):
            return ["policy_recheck"]  # dynamic re-plan: look for exclusions behind the signal
        if "summary" not in state:
            return ["summarizer"]
        return END

    # --- agents ----------------------------------------------------------------------------

    def _ask(
        self, name: str, schema: type[S], section: str, content: str, t0: float
    ) -> tuple[S, AgentRun]:
        start = self.clock()
        runnable = self.llm.with_structured_output(
            schema, method="json_schema", strict=True, include_raw=True
        )
        out = runnable.invoke(
            [SystemMessage(content=self.prompts[section]), HumanMessage(content=content)]
        )
        if (
            not isinstance(out, dict)
            or out.get("parsing_error") is not None
            or out.get("parsed") is None
        ):
            raise ValueError("Invalid agent output")
        parsed = out["parsed"]
        # Re-run every validator on a plain copy; never trust a constructed SDK model.
        checked = schema.model_validate(
            parsed.model_dump(mode="json") if isinstance(parsed, StrictModel) else parsed
        )
        usage = getattr(out.get("raw"), "usage_metadata", None) or {}
        run = AgentRun(
            name,
            "llm",
            start - t0,
            self.clock() - start,
            int(usage.get("input_tokens", 0)),
            int(usage.get("output_tokens", 0)),
        )
        return checked, run

    def _reviewer(self, state: TeamState) -> TeamState:
        out, run = self._ask(REVIEWER, ReviewOut, "REVIEWER", state["form"], state["t0"])
        return {"items": out.items, "runs": [run]}

    def _risk(self, state: TeamState) -> TeamState:
        out, run = self._ask(RISK, RiskOut, "RISK", state["form"], state["t0"])
        return {"risk_signals": out.risk_signals, "runs": [run]}

    def _policy(self, state: TeamState, *, recheck: bool) -> TeamState:
        done: TeamState = {"replanned": True} if recheck else {}
        if self.retriever is None:
            return done
        facts = case_facts(state["form"])
        if recheck:
            signals = " ".join(s.signal for s in state.get("risk_signals", []))
            queries = [f"보상하지 않는 사항 면책. {signals}", facts]
        else:
            bundle = json.loads(state["form"].removeprefix("JEV_TEXT_FORM="))["bundle"]
            queries = [INTENT_QUERIES[bundle], CLAIM_QUERY, facts]
        start = self.clock()
        hits = self._search(queries, self.top_k if not recheck else max(3, self.top_k // 2))
        searched = AgentRun(RETRIEVER, "retriever", start - state["t0"], self.clock() - start)
        clauses = "\n\n".join(
            f"[{h.clause.id}] {h.clause.product} {h.clause.article}({h.clause.title})\n{h.clause.text[:900]}"
            for h in hits
        )
        out, run = self._ask(
            POLICY_RECHECK if recheck else POLICY,
            PolicyOut,
            "POLICY",
            f"사건 요약: {facts}\n\n검색된 조항:\n{clauses}",
            state["t0"],
        )
        known = {h.clause.id for h in hits}
        if any(pick.clause_id not in known for pick in out.refs):
            raise ValueError("Agent cited a clause that was not retrieved")
        return {**done, "hits": hits, "picks": out.refs, "runs": [searched, run]}

    def _search(self, queries: list[str], limit: int) -> list[Hit]:
        assert self.retriever is not None
        per_query = max(2, limit // len(queries))
        merged: dict[str, Hit] = {}
        for query in queries:
            for hit in self.retriever.search(query, per_query):
                merged.setdefault(hit.clause.id, hit)
        return list(merged.values())[:limit]

    def _summarizer(self, state: TeamState) -> TeamState:
        titles = {
            h.clause.id: f"{h.clause.product} {h.clause.article}({h.clause.title})"
            for h in state["hits"]
        }
        brief = {
            "items": [
                {"item": i.item, "judgment": i.judgment, "supplement": i.supplement}
                for i in state["items"]
            ],
            "risk_signals": [
                {"signal": s.signal, "level": s.level, "reason": s.reason}
                for s in state.get("risk_signals", [])
            ],
            "policy_refs": [
                {"clause": titles[p.clause_id], "reason": p.reason} for p in state["picks"]
            ],
        }
        out, run = self._ask(
            SUMMARY, SummaryOut, "SUMMARY", json.dumps(brief, ensure_ascii=False), state["t0"]
        )
        return {"summary": out.summary, "runs": [run]}
