from datetime import datetime, timedelta, timezone
from typing import Literal
from uuid import UUID, uuid4

from app.application.resilient_execution import ExecutionStats
from app.chains.orchestrator import WorkflowReport, WorkflowStage
from app.chains.release import ACTIVE_RELEASE
from app.core.config import Settings
from app.observability.metadata import (
    EXECUTION_KEYS,
    ROUTE_KEYS,
    WORKFLOW_KEYS,
    checked_metadata,
    component,
)
from app.providers.routing_policy import RouteDecision
from app.schemas.jev import VerificationSummary

# Child runs show each step in the LangSmith trace tree; names and metadata are fixed constants.
RunType = Literal["chain", "llm"]
STAGE_RUNS: dict[WorkflowStage, tuple[str, RunType]] = {
    WorkflowStage.PREPARE: ("① 빠른 검사 · 텍스트 폼", "chain"),
    WorkflowStage.JUDGMENT: ("J · LLM 호출", "llm"),
    WorkflowStage.EVIDENCE: ("E · 근거 대조", "chain"),
    WorkflowStage.VERIFICATION: ("V · 구조 검증", "chain"),
}
LS_PROVIDERS = {"openai": "openai", "gemini": "google_genai"}
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
            return trace_id, "RECORDED"
        except Exception:
            # Never log provider exceptions, document data, or SDK error payloads.
            return None, "FAILED"

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
