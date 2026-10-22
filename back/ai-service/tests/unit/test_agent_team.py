import json
from unittest.mock import Mock

import pytest
from langchain_core.embeddings import Embeddings
from langchain_core.messages import AIMessage

from app.agents.provider import MultiAgentProvider
from app.agents.team import POLICY, POLICY_RECHECK, RETRIEVER, REVIEWER, RISK, SUMMARY, case_facts
from app.application.jev_service import JevService
from app.core.config import Settings
from app.core.errors import InvalidProviderResponse
from app.jev.precheck import precheck
from app.jev.workflow import run_workflow
from app.rag.chunking import load_clauses
from app.rag.store import InMemoryRetriever


class BigramEmbedding(Embeddings):
    """Offline embedding: hashed character bigrams (enough for ranking in tests)."""

    def embed_query(self, text):
        vector = [0.0] * 64
        for a, b in zip(text, text[1:], strict=False):
            vector[hash(a + b) % 64] += 1.0
        return vector

    def embed_documents(self, texts):
        return [self.embed_query(t) for t in texts]


class FakeChat:
    """Stands in for a LangChain chat model: answers per output schema, records calls."""

    def __init__(self, level="LOW", cite=None):
        self.level, self.cite, self.calls = level, cite, []

    def with_structured_output(self, schema, **kwargs):
        assert kwargs == {"method": "json_schema", "strict": True, "include_raw": True}
        chat = self

        class Runnable:
            def invoke(self, messages):
                chat.calls.append((schema.__name__, messages[1].content))
                return {
                    "parsed": schema.model_validate(
                        chat.answer(schema.__name__, messages[1].content)
                    ),
                    "raw": AIMessage(
                        content="",
                        usage_metadata={
                            "input_tokens": 100,
                            "output_tokens": 20,
                            "total_tokens": 120,
                        },
                    ),
                    "parsing_error": None,
                }

        return Runnable()

    def answer(self, schema, content):
        if schema == "ReviewOut":
            doc = json.loads(content.removeprefix("JEV_TEXT_FORM="))["documents"][0]
            evidence = [
                {"document_id": doc["document_id"], "page": 1, "description": "진단명 기재"}
            ]
            return {
                "items": [
                    {
                        "item": "진단명 기재",
                        "judgment": "SUITABLE",
                        "evidence": evidence,
                        "confidence": 0.9,
                        "verification_reason": "기재 확인",
                        "supplement": "",
                    }
                ]
            }
        if schema == "RiskOut":
            doc = json.loads(content.removeprefix("JEV_TEXT_FORM="))["documents"][0]
            evidence = [{"document_id": doc["document_id"], "page": 1, "description": "날짜 확인"}]
            return {
                "risk_signals": [
                    {
                        "signal": "날짜 확인 필요",
                        "level": self.level,
                        "reason": "원본 확인",
                        "evidence": evidence,
                    }
                ]
            }
        if schema == "PolicyOut":
            first = self.cite or content.split("검색된 조항:\n[", 1)[1].split("]", 1)[0]
            return {"refs": [{"clause_id": first, "reason": "청구 서류 요건을 확인할 조항"}]}
        return {"summary": "합성 요약입니다. 최종 판단은 손해사정사가 합니다."}


@pytest.fixture(scope="module")
def retriever():
    return InMemoryRetriever(load_clauses(), BigramEmbedding())


def provider(chat, retriever):
    config = Settings(
        app_env="local",
        jev_provider="openai",
        openai_api_key="k",
        openai_model="m",
        jev_service_token="t",
    )
    return MultiAgentProvider(config, "m", retriever=retriever, llm=chat)


def names(result):
    return [run.name for run in result.agent_runs]


def test_supervisor_runs_specialists_then_summary_and_cites_retrieved_clauses(
    sample_request, retriever
):
    chat = FakeChat()
    result = run_workflow(sample_request, provider(chat, retriever))
    assert set(names(result)) == {REVIEWER, RISK, POLICY, RETRIEVER, SUMMARY}
    assert names(result)[-1] == SUMMARY  # the summary agent runs after the specialists
    assert result.input_tokens == 400 and result.output_tokens == 80 and result.usage_complete
    ref = result.policy_refs[0]
    clause = next(c for c in load_clauses() if c.id == ref.clause_id)
    assert (ref.article, ref.title) == (clause.article, clause.title)  # text from the corpus
    assert result.findings.summary.startswith("합성 요약")
    assert result.verification.human_review == "REQUIRED"


def test_medium_risk_triggers_one_exclusion_recheck(sample_request, retriever):
    result = run_workflow(sample_request, provider(FakeChat(level="MEDIUM"), retriever))
    assert names(result).count(POLICY_RECHECK) == 1
    assert names(result)[-1] == SUMMARY


def test_no_recheck_without_retriever_or_signal(sample_request, retriever):
    assert POLICY_RECHECK not in names(
        run_workflow(sample_request, provider(FakeChat(), retriever))
    )
    result = run_workflow(sample_request, provider(FakeChat(level="HIGH"), None))
    assert POLICY not in names(result) and result.policy_refs == ()


def test_unretrieved_clause_citation_is_rejected(sample_request, retriever):
    with pytest.raises(InvalidProviderResponse):
        run_workflow(sample_request, provider(FakeChat(cite="basic:a99:1"), retriever))


def test_retrieval_query_has_care_facts_but_no_identity(sample_request):
    facts = case_facts(precheck(sample_request).form())
    assert "가상 질환" in facts and "J20.9" in facts
    for secret in ("테스트-김하늘", "환자-A", "900101"):
        assert secret not in facts


def test_service_response_carries_policy_refs(sample_request, retriever):
    trace = Mock()
    trace.record.return_value = (None, "DISABLED")
    response = JevService(provider(FakeChat(), retriever), trace).run(sample_request)
    assert response.provider == "openai" and len(response.policy_refs) == 1
    assert response.verification.confidence_basis == "MODEL_SELF_ASSESSMENT"


def test_corpus_chunks_are_article_scoped_and_unique():
    clauses = load_clauses()
    assert len(clauses) == len({c.id for c in clauses}) > 100
    products = {c.product for c in clauses}
    # National standard terms plus two insurers' actual 실손 terms.
    assert {
        "표준약관 기본형(급여)",
        "표준약관 특약1(중증 비급여)",
        "표준약관 특약2(비중증 비급여)",
    } <= products
    assert any(p.startswith("삼성화재") for p in products)
    assert any(p.startswith("한화생명") for p in products)
    claim = next(c for c in clauses if c.id == "basic:a7:1")
    assert claim.title == "보험금의 청구" and "진료비 세부산정내역" in claim.text
    for insurer_claim in ("ssbasic:a3:1", "hwmain:a3:1"):
        assert next(c for c in clauses if c.id == insurer_claim).title == "보장종목별 보상내용"
    assert all(len(c.text) <= 1200 for c in clauses)


def test_langsmith_tree_nests_agents_under_j_without_content(
    monkeypatch, sample_request, retriever
):
    from datetime import datetime, timezone

    from langsmith import Client

    from app.observability.langsmith import SafeTrace

    runs = []
    monkeypatch.setattr("requests.Session.request", Mock(side_effect=AssertionError("no network")))
    monkeypatch.setattr(Client, "_create_run", lambda self, data, **kwargs: runs.append(data))
    result = run_workflow(sample_request, provider(FakeChat(level="MEDIUM"), retriever))
    config = Settings(app_env="local", langsmith_enabled=True, langsmith_api_key="test-key")
    SafeTrace(config).record(
        datetime.now(timezone.utc),
        "openai",
        "m",
        True,
        1,
        1,
        orchestration=None,
        agents=result.agent_runs,
    )
    # Without a workflow report only the root is written; add one to see the full tree.
    from app.jev.orchestrator import STAGES, WorkflowReport

    runs.clear()
    trace_id, status = SafeTrace(config).record(
        datetime.now(timezone.utc),
        "openai",
        "m",
        True,
        1,
        1,
        orchestration=WorkflowReport(STAGES, None, (0.01, 3.0, 0.01, 0.01)),
        agents=result.agent_runs,
    )
    assert status == "RECORDED"
    j = next(r for r in runs if r["name"] == "J · 멀티 에이전트 (LangGraph)")
    agents = [r for r in runs if r.get("parent_run_id") == j["id"]]
    assert {r["name"] for r in agents} >= {
        REVIEWER,
        RISK,
        POLICY,
        POLICY_RECHECK,
        RETRIEVER,
        SUMMARY,
    }
    assert {r["run_type"] for r in agents} == {"llm", "retriever"}
    assert all(r["inputs"] == {} for r in runs)
    dump = str(runs)
    for secret in ("가상 질환", "J20.9", "테스트-김하늘", "보험금의 청구", "청구 서류 요건"):
        assert secret not in dump
