from datetime import datetime, timezone

from httpx import TimeoutException

from app.chains.jev_workflow import run_workflow
from app.chains.orchestrator import WorkflowProgress
from app.chains.precheck import RULES_MODEL, precheck
from app.chains.release import ACTIVE_RELEASE
from app.core.errors import (
    CircuitOpen,
    InvalidProviderResponse,
    ProviderFailure,
    ProviderTimeout,
    TransientProviderFailure,
)
from app.core.resources import load_jev_prompt
from app.observability.langsmith import SafeTrace
from app.providers.base import Provider
from app.providers.routing_policy import RouteDecision
from app.schemas.jev import JevRequest, JevResponse, VerificationSummary


class JevService:
    def __init__(
        self, provider: Provider, trace: SafeTrace, route: RouteDecision | None = None
    ) -> None:
        load_jev_prompt()
        if route and (provider.name, provider.model) != (
            route.profile.provider,
            route.profile.model,
        ):
            raise ValueError("Provider does not match routing decision")
        self.provider = provider
        self.trace = trace
        self.route = route

    def rule_response(self, request: JevRequest, started: datetime) -> JevResponse | None:
        """Obvious gaps go straight back to the claimant; only complete bundles reach a model."""
        # ponytail: the model path extracts the PDFs again in PREPARE; pass the
        # Precheck through the executor if extraction time ever matters.
        try:
            check = precheck(request)
            if check.complete:
                return None
            findings = check.claimant_findings()
            verification = VerificationSummary(
                method="STRUCTURAL",
                factual_consistency="NOT_VERIFIED",
                confidence_basis="RULE_PRESENCE_CHECK",
                human_review="REQUIRED",
            )
        except Exception:
            raise InvalidProviderResponse() from None
        trace_id, trace_status = self.trace.record(
            started, "rules", RULES_MODEL, True, verification=verification
        )
        return JevResponse.model_validate(
            {
                "request_id": request.request_id,
                "items": findings.items,
                "risk_signals": findings.risk_signals,
                "summary": findings.summary,
                "provider": "rules",
                "model": RULES_MODEL,
                **ACTIVE_RELEASE.metadata(),
                "trace_id": trace_id,
                "trace_status": trace_status,
                "verification": verification,
            }
        )

    def run(self, request: JevRequest) -> JevResponse:
        started = datetime.now(timezone.utc)
        if (response := self.rule_response(request, started)) is not None:
            return response
        progress = WorkflowProgress()
        try:
            result = run_workflow(request, self.provider, progress=progress)
        except (TimeoutError, TimeoutException):
            self.trace.record(
                started,
                self.provider.name,
                self.provider.model,
                False,
                route=self.route,
                orchestration=progress.report(),
            )
            raise ProviderTimeout() from None
        except ValueError:
            self.trace.record(
                started,
                self.provider.name,
                self.provider.model,
                False,
                route=self.route,
                orchestration=progress.report(),
            )
            raise InvalidProviderResponse() from None
        except ProviderFailure as error:
            self.trace.record(
                started,
                self.provider.name,
                self.provider.model,
                False,
                route=self.route,
                orchestration=progress.report(),
            )
            # Preserve the portable error category, never adapter exception text.
            if isinstance(error, ProviderTimeout):
                raise ProviderTimeout() from None
            if isinstance(error, InvalidProviderResponse):
                raise InvalidProviderResponse() from None
            if isinstance(error, CircuitOpen):
                raise CircuitOpen() from None
            if isinstance(error, TransientProviderFailure):
                raise TransientProviderFailure(retry_after=error.retry_after) from None
            raise ProviderFailure() from None
        except Exception:
            self.trace.record(
                started,
                self.provider.name,
                self.provider.model,
                False,
                route=self.route,
                orchestration=progress.report(),
            )
            raise ProviderFailure() from None
        trace_id, trace_status = self.trace.record(
            started,
            self.provider.name,
            self.provider.model,
            True,
            result.input_tokens,
            result.output_tokens,
            route=self.route,
            verification=result.verification,
            orchestration=progress.report(),
        )
        try:
            return JevResponse.model_validate(
                {
                    "request_id": request.request_id,
                    "items": result.findings.items,
                    "risk_signals": result.findings.risk_signals,
                    "summary": result.findings.summary,
                    "provider": self.provider.name,
                    "model": self.provider.model,
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
