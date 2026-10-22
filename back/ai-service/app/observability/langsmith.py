from datetime import datetime, timedelta, timezone
from typing import Literal
from uuid import UUID, uuid4

from app.application.resilient_execution import ExecutionStats
from app.core.config import Settings
from app.jev.orchestrator import WorkflowReport, WorkflowStage
from app.jev.release import ACTIVE_RELEASE
from app.observability.metadata import (
    EXECUTION_KEYS,
    ROUTE_KEYS,
    WORKFLOW_KEYS,
    checked_metadata,
    component,
)
from app.providers.base import AgentRun
from app.providers.routing_policy import RouteDecision
from app.schemas.jev import VerificationSummary

# Child runs show each step in the LangSmith trace tree; names and metadata are fixed constants.
RunType = Literal["chain", "llm", "retriever"]
STAGE_RUNS: dict[WorkflowStage, tuple[str, RunType]] = {
    WorkflowStage.PREPARE: ("① 빠른 검사 · 텍스트 폼", "chain"),
    WorkflowStage.JUDGMENT: ("J · LLM 호출", "llm"),
    WorkflowStage.EVIDENCE: ("E · 근거 대조", "chain"),
    WorkflowStage.VERIFICATION: ("V · 구조 검증", "chain"),
}
LS_PROVIDERS = {"openai": "openai", "gemini": "google_genai"}
# Multi-agent J stage: agent display name -> fixed metadata key (names come from app.agents.team).
AGENT_KEYS = {
    "① 서류 검토 에이전트": "reviewer",
    "② 약관 검색 에이전트": "policy",
    "② 약관 재검색 (재계획)": "policy_recheck",
    "pgvector 약관 검색": "retriever",
    "③ 의심 검토 에이전트": "risk",
    "④ 요약 에이전트": "summary",
}
USAGE_KEYS = {"input_tokens", "output_tokens", "total_tokens"}


def usage_only(outputs: object) -> dict[str, object]:
    """Output mask: only integer token usage survives, so LangSmith can count tokens."""
    usage = outputs.get("usage_metadata") if isinstance(outputs, dict) else None
    if (
        isinstance(usage, dict)
        and set(usage) <= USAGE_KEYS
        and all(type(v) is int and v >= 0 for v in usage.values())
    ):
        return {"usage_metadata": dict(usage)}
    return {}


def dotted(start: datetime, run_id: UUID) -> str:
    return f"{start:%Y%m%dT%H%M%S%f}Z{run_id}"


class SafeTrace:
    """Only explicit numeric/constant metadata crosses the telemetry boundary."""

    def __init__(self, config: Settings) -> None:
        self.config = config

    def record(
        self,
        started: datetime,
        provider: str,
        model: str,
        success: bool,
        input_tokens: int = 0,
        output_tokens: int = 0,
        *,
        route: RouteDecision | None = None,
        execution: ExecutionStats | None = None,
        verification: VerificationSummary | None = None,
        orchestration: WorkflowReport | None = None,
        agents: tuple[AgentRun, ...] = (),
    ) -> tuple[UUID | None, str]:
        if not self.config.langsmith_enabled:
            return None, "DISABLED"
        trace_id = uuid4()
        try:
            from langsmith import Client

            verification_metadata = {}
            if success and verification is not None:
                checked = VerificationSummary.model_validate(verification)
                verification_metadata = {
                    f"verification_{key}": value for key, value in checked.model_dump().items()
                }
            metadata = checked_metadata(
                {
                    "provider": provider,
                    "model": model,
                    **ACTIVE_RELEASE.metadata(),
                    "input_tokens": input_tokens,
                    "output_tokens": output_tokens,
                    **(component(route.metadata(), ROUTE_KEYS) if route else {}),
                    **(component(execution.metadata(), EXECUTION_KEYS) if execution else {}),
                    **verification_metadata,
                    **(component(orchestration.metadata(), WORKFLOW_KEYS) if orchestration else {}),
                }
            )
            client = Client(
                api_key=self.config.langsmith_api_key.get_secret_value(),
                api_url=self.config.langsmith_endpoint,
                hide_inputs=True,
                hide_outputs=usage_only,
                hide_metadata=checked_metadata,
                auto_batch_tracing=False,
                omit_traced_runtime_info=True,
                tracing_sampling_rate=1.0,
                timeout_ms=(2000, 3000),
            )
            ended = datetime.now(timezone.utc)
            root_order = dotted(started, trace_id)
            client.create_run(
                id=trace_id,
                trace_id=trace_id,
                dotted_order=root_order,
                name="jev-analysis",
                run_type="chain",
                project_name=self.config.langsmith_project,
                inputs={},
                outputs={},
                start_time=started,
                end_time=ended,
                error=None if success else "ANALYSIS_FAILED",
                extra={"metadata": metadata},
            )
            report = orchestration or (execution.orchestration if execution else None)
            for name, run_type, stage, start, end, failed in self._steps(
                provider, started, ended, report
            ):
                child = uuid4()
                child_metadata: dict[str, object] = {"stage": stage}
                outputs: dict[str, object] = {}
                if stage == "J" and agents:
                    # LangGraph team: J is a chain whose children are the agents themselves.
                    name, run_type = "J · 멀티 에이전트 (LangGraph)", "chain"
                if run_type == "llm" and provider in LS_PROVIDERS:
                    child_metadata.update(ls_provider=LS_PROVIDERS[provider], ls_model_name=model)
                    outputs = {
                        "usage_metadata": {
                            "input_tokens": input_tokens,
                            "output_tokens": output_tokens,
                            "total_tokens": input_tokens + output_tokens,
                        }
                    }
                client.create_run(
                    id=child,
                    trace_id=trace_id,
                    parent_run_id=trace_id,
                    dotted_order=f"{root_order}.{dotted(start, child)}",
                    name=name,
                    run_type=run_type,
                    project_name=self.config.langsmith_project,
                    inputs={},
                    outputs=outputs,
                    start_time=start,
                    end_time=end,
                    error="STAGE_FAILED" if failed else None,
                    extra={"metadata": checked_metadata(child_metadata)},
                )
                if stage == "J" and agents:
                    child_order = f"{root_order}.{dotted(start, child)}"
                    for agent in agents:
                        self._agent_run(
                            client, agent, trace_id, child, child_order, start, end, provider, model
                        )
            return trace_id, "RECORDED"
        except Exception:
            # Never log provider exceptions, document data, or SDK error payloads.
            return None, "FAILED"

    def _agent_run(
        self,
        client: object,
        agent: AgentRun,
        trace_id: UUID,
        parent: UUID,
        parent_order: str,
        stage_start: datetime,
        stage_end: datetime,
        provider: str,
        model: str,
    ) -> None:
        run_id = uuid4()
        start = min(stage_end, stage_start + timedelta(seconds=agent.offset))
        end = min(stage_end, start + timedelta(seconds=agent.seconds))
        metadata: dict[str, object] = {"stage": "J", "agent": AGENT_KEYS[agent.name]}
        outputs: dict[str, object] = {}
        if agent.kind == "llm":
            if provider in LS_PROVIDERS:
                metadata.update(ls_provider=LS_PROVIDERS[provider], ls_model_name=model)
            outputs = {
                "usage_metadata": {
                    "input_tokens": agent.input_tokens,
                    "output_tokens": agent.output_tokens,
                    "total_tokens": agent.input_tokens + agent.output_tokens,
                }
            }
        client.create_run(  # type: ignore[attr-defined]
            id=run_id,
            trace_id=trace_id,
            parent_run_id=parent,
            dotted_order=f"{parent_order}.{dotted(start, run_id)}",
            name=agent.name,
            run_type="llm" if agent.kind == "llm" else "retriever",
            project_name=self.config.langsmith_project,
            inputs={},
            outputs=outputs,
            start_time=start,
            end_time=end,
            extra={"metadata": checked_metadata(metadata)},
        )

    @staticmethod
    def _steps(
        provider: str, started: datetime, ended: datetime, report: WorkflowReport | None
    ) -> list[tuple[str, RunType, str, datetime, datetime, bool]]:
        if provider == "rules":
            return [("① 빠른 검사 · LLM 미호출", "chain", "PRECHECK", started, ended, False)]
        if report is None or not report.durations:
            return []
        stages = (*report.completed, *((report.failed,) if report.failed else ()))
        # Stages run back to back at the end of the request, after the service-level check.
        cursor = max(started, ended - timedelta(seconds=sum(report.durations)))
        steps: list[tuple[str, RunType, str, datetime, datetime, bool]] = []
        for stage, seconds in zip(stages, report.durations, strict=True):
            end = min(ended, cursor + timedelta(seconds=seconds))
            name, run_type = STAGE_RUNS[stage]
            steps.append((name, run_type, stage.value, cursor, end, stage == report.failed))
            cursor = end
        return steps
