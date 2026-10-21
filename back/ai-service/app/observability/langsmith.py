from datetime import datetime, timezone
from uuid import UUID, uuid4

from app.application.resilient_execution import ExecutionStats
from app.chains.orchestrator import WorkflowReport
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
                hide_outputs=True,
                hide_metadata=checked_metadata,
                auto_batch_tracing=False,
                omit_traced_runtime_info=True,
                tracing_sampling_rate=1.0,
                timeout_ms=(2000, 3000),
            )
            client.create_run(
                id=trace_id,
                name="jev-analysis",
                run_type="chain",
                project_name=self.config.langsmith_project,
                inputs={},
                outputs={},
                start_time=started,
                end_time=datetime.now(timezone.utc),
                error=None if success else "ANALYSIS_FAILED",
                extra={"metadata": metadata},
            )
            return trace_id, "RECORDED"
        except Exception:
            # Never log provider exceptions, document data, or SDK error payloads.
            return None, "FAILED"
