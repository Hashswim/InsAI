from datetime import datetime, timezone
from app.chains.jev_workflow import run_workflow
from app.core.errors import ProviderFailure
from app.schemas.jev import JevRequest, JevResponse


class JevService:
    def __init__(self, provider, trace):
        self.provider = provider
        self.trace = trace

    def run(self, request: JevRequest) -> JevResponse:
        started = datetime.now(timezone.utc)
        try:
            result = run_workflow(request, self.provider)
        except Exception:
            self.trace.record(started, self.provider.name, self.provider.model, False)
            raise ProviderFailure() from None
        trace_id, trace_status = self.trace.record(
            started, self.provider.name, self.provider.model, True,
            result.input_tokens, result.output_tokens,
        )
        return JevResponse(
            request_id=request.request_id, items=result.findings.items,
            provider=self.provider.name, model=self.provider.model,
            trace_id=trace_id, trace_status=trace_status,
            input_tokens=result.input_tokens, output_tokens=result.output_tokens,
        )
