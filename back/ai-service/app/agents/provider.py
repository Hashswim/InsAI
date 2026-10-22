"""The agent team behind the single Provider port, so retries, budgets and J/E/V stay as-is."""

from typing import Any

import httpx

from app.agents.team import AgentTeam, TeamState
from app.core.config import Settings
from app.core.errors import (
    InvalidProviderResponse,
    ProviderFailure,
    ProviderTimeout,
    TransientProviderFailure,
)
from app.jev.structured_output import validate_findings
from app.providers.base import ProviderRequest, ProviderResult
from app.providers.catalog import JEV_OUTPUT_TOKENS
from app.rag.store import Retriever
from app.schemas.jev import Findings, PolicyRef


def chat_model(config: Settings, model: str) -> Any:
    """One LangChain chat model shared by every agent; the executor owns retries."""
    if config.jev_provider == "openai":
        from langchain_openai import ChatOpenAI

        return ChatOpenAI(
            model=model,
            api_key=config.openai_api_key.get_secret_value(),  # type: ignore[arg-type]
            use_responses_api=True,
            store=False,
            reasoning_effort=config.openai_reasoning_effort,
            verbosity=config.openai_verbosity,
            max_completion_tokens=JEV_OUTPUT_TOKENS,
            max_retries=0,
            timeout=config.jev_attempt_timeout_seconds,
            http_client=httpx.Client(trust_env=False, follow_redirects=False),
        )
    if config.jev_provider == "gemini":
        from langchain_google_genai import ChatGoogleGenerativeAI

        return ChatGoogleGenerativeAI(
            model=model,
            api_key=config.google_api_key.get_secret_value(),
            temperature=0,
            timeout=config.jev_attempt_timeout_seconds,
            max_retries=1,
            max_output_tokens=JEV_OUTPUT_TOKENS,
            vertexai=False,
            client_args={"trust_env": False, "follow_redirects": False},
        )
    raise ValueError("Multi-agent analysis requires a real provider")


def policy_refs(state: TeamState) -> tuple[PolicyRef, ...]:
    clauses = {hit.clause.id: hit.clause for hit in state.get("hits", [])}
    refs: dict[str, PolicyRef] = {}
    for pick in state.get("picks", []):
        clause = clauses[pick.clause_id]  # team already rejected unretrieved ids
        refs.setdefault(
            clause.id,
            PolicyRef(
                clause_id=clause.id,
                product=clause.product,
                article=clause.article,
                title=clause.title[:120],
                excerpt=" ".join(clause.text.split())[:400],
                reason=pick.reason,
            ),
        )
    return tuple(refs.values())[:10]


class MultiAgentProvider:
    def __init__(
        self, config: Settings, model: str, *, retriever: Retriever | None, llm: Any = None
    ) -> None:
        self.name: str = config.jev_provider
        self.model = model
        self.team = AgentTeam(
            llm if llm is not None else chat_model(config, model), retriever, config.jev_rag_top_k
        )

    def analyze(self, request: ProviderRequest) -> ProviderResult:
        try:
            state = self.team.run(request.form)
        except (TimeoutError, httpx.TimeoutException):
            raise ProviderTimeout() from None
        except ValueError:
            raise InvalidProviderResponse() from None
        except Exception as error:
            # SDK/graph errors may embed request data; keep only the portable category.
            if type(error).__name__ in {"APITimeoutError"}:
                raise ProviderTimeout() from None
            if type(error).__name__ in {"APIConnectionError", "InternalServerError"}:
                raise TransientProviderFailure() from None
            raise ProviderFailure() from None
        try:
            findings = validate_findings(
                Findings(
                    items=state["items"],
                    risk_signals=state.get("risk_signals", []),
                    summary=state["summary"],
                )
            )
            runs = tuple(sorted(state["runs"], key=lambda run: run.offset))
            llm_runs = [run for run in runs if run.kind == "llm"]
            return ProviderResult(
                findings,
                sum(run.input_tokens for run in llm_runs),
                sum(run.output_tokens for run in llm_runs),
                usage_complete=all(run.input_tokens and run.output_tokens for run in llm_runs),
                policy_refs=policy_refs(state),
                agent_runs=runs,
            )
        except (KeyError, ValueError, TypeError):
            raise InvalidProviderResponse() from None
