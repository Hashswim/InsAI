from datetime import datetime, timezone

from httpx import TimeoutException

from app.chains.jev_workflow import run_workflow
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
from app.schemas.jev import JevRequest, JevResponse


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

    def run(self, request: JevRequest) -> JevResponse:
        started = datetime.now(timezone.utc)
        try:
            result = run_workflow(request, self.provider)
        except (TimeoutError, TimeoutException):
            self.trace.record(
                started, self.provider.name, self.provider.model, False, route=self.route
            )
            raise ProviderTimeout() from None
        except ValueError:
            self.trace.record(
                started, self.provider.name, self.provider.model, False, route=self.route
            )
            raise InvalidProviderResponse() from None
        except ProviderFailure as error:
            self.trace.record(
                started, self.provider.name, self.provider.model, False, route=self.route
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
                started, self.provider.name, self.provider.model, False, route=self.route
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
        )
        try:
            return JevResponse.model_validate(
                {
                    "request_id": request.request_id,
                    "items": result.findings.items,
                    "provider": self.provider.name,
                    "model": self.provider.model,
                    **ACTIVE_RELEASE.metadata(),
                    "trace_id": trace_id,
                    "trace_status": trace_status,
                    "input_tokens": result.input_tokens,
                    "output_tokens": result.output_tokens,
                }
            )
        except ValueError:
            raise InvalidProviderResponse() from None
