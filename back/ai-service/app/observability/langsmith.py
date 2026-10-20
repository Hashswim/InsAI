from datetime import datetime, timezone
from uuid import uuid4
from app.core.config import Settings


class SafeTrace:
    """Only explicit numeric/constant metadata crosses the telemetry boundary."""

    def __init__(self, config: Settings):
        self.config = config

    def record(self, started, provider, model, success, input_tokens=0, output_tokens=0):
        if not self.config.langsmith_enabled:
            return None, "DISABLED"
        trace_id = uuid4()
        try:
            from langsmith import Client
            client = Client(
                api_key=self.config.langsmith_api_key,
                api_url=self.config.langsmith_endpoint,
                hide_inputs=True, hide_outputs=True,
                auto_batch_tracing=False,
            )
            client.create_run(
                id=trace_id, name="jev-analysis", run_type="chain",
                project_name=self.config.langsmith_project,
                inputs={}, outputs={}, start_time=started,
                end_time=datetime.now(timezone.utc),
                error=None if success else "ANALYSIS_FAILED",
                extra={"metadata": {
                    "provider": provider, "model": model, "prompt_version": "jev-v1",
                    "input_tokens": input_tokens, "output_tokens": output_tokens,
                }},
            )
            return trace_id, "RECORDED"
        except Exception:
            # Never log provider exceptions, document data, or SDK error payloads.
            return None, "FAILED"
