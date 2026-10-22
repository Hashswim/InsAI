from datetime import datetime, timezone

from app.application.jev_service import JevService
from app.application.resilient_execution import ExecutionFailure, ResilientExecutor
from app.core.errors import InvalidProviderResponse
from app.jev.release import ACTIVE_RELEASE
from app.observability.langsmith import SafeTrace
from app.providers.base import Provider
from app.providers.routing_policy import RouteDecision
from app.schemas.jev import JevRequest, JevResponse


class ResilientJevService(JevService):
    """One logical trace/response; selected identity remains request-local."""

    def __init__(
        self,
        provider: Provider,
        trace: SafeTrace,
        route: RouteDecision,
        executor: ResilientExecutor,
    ) -> None:
        super().__init__(provider, trace, route)
        self.executor = executor

    def run(self, request: JevRequest) -> JevResponse:
        started = datetime.now(timezone.utc)
        if (response := self.rule_response(request, started)) is not None:
            return response
        try:
            outcome = self.executor.execute(request)
        except ExecutionFailure as failure:
            route = failure.route
            self.trace.record(
                started,
                route.profile.provider,
                route.profile.model,
                False,
                route=route,
                execution=failure.stats,
            )
            raise failure.error from None
        route = outcome.route
        result = outcome.result
        trace_id, trace_status = self.trace.record(
            started,
            route.profile.provider,
            route.profile.model,
            True,
            result.input_tokens,
            result.output_tokens,
            route=route,
            execution=outcome.stats,
            verification=result.verification,
            agents=result.agent_runs,
        )
        try:
            return JevResponse.model_validate(
                {
                    "request_id": request.request_id,
                    "items": result.findings.items,
                    "risk_signals": result.findings.risk_signals,
                    "summary": result.findings.summary,
                    "policy_refs": list(result.policy_refs),
                    "provider": route.profile.provider,
                    "model": route.profile.model,
                    **ACTIVE_RELEASE.metadata(),
                    "trace_id": trace_id,
                    "trace_status": trace_status,
                    "input_tokens": result.input_tokens,
                    "output_tokens": result.output_tokens,
                    "verification": result.verification,
                }
            )
        except ValueError:
            raise InvalidProviderResponse() from None
