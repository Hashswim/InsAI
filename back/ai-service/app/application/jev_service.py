from datetime import datetime, timezone

from httpx import TimeoutException

from app.chains.jev_workflow import run_workflow
from app.core.errors import InvalidProviderResponse, ProviderFailure, ProviderTimeout
from app.observability.langsmith import SafeTrace
from app.providers.base import Provider
from app.schemas.jev import JevRequest, JevResponse


class JevService:
    def __init__(self, provider: Provider, trace: SafeTrace) -> None:
        self.provider = provider
        self.trace = trace

    def run(self, request: JevRequest) -> JevResponse:
        started = datetime.now(timezone.utc)
        try:
            result = run_workflow(request, self.provider)
        except (TimeoutError, TimeoutException):
            self.trace.record(started, self.provider.name, self.provider.model, False)
            raise ProviderTimeout() from None
        except ValueError:
            self.trace.record(started, self.provider.name, self.provider.model, False)
            raise InvalidProviderResponse() from None
        except ProviderFailure as error:
            self.trace.record(started, self.provider.name, self.provider.model, False)
            # Preserve the portable error category, never adapter exception text.
            if isinstance(error, ProviderTimeout):
                raise ProviderTimeout() from None
            if isinstance(error, InvalidProviderResponse):
                raise InvalidProviderResponse() from None
            raise ProviderFailure() from None
        except Exception:
            self.trace.record(started, self.provider.name, self.provider.model, False)
            raise ProviderFailure() from None
        trace_id, trace_status = self.trace.record(
            started,
            self.provider.name,
            self.provider.model,
            True,
            result.input_tokens,
            result.output_tokens,
        )
        try:
            return JevResponse.model_validate(
                {
                    "request_id": request.request_id,
                    "items": result.findings.items,
                    "provider": self.provider.name,
                    "model": self.provider.model,
                    "trace_id": trace_id,
                    "trace_status": trace_status,
                    "input_tokens": result.input_tokens,
                    "output_tokens": result.output_tokens,
                }
            )
        except ValueError:
            raise InvalidProviderResponse() from None
