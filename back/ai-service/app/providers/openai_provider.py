"""Stateless Responses API adapter; no Files API uploads or automatic retries."""

from typing import Any

import httpx

from app.core.config import Settings
from app.core.errors import (
    InvalidProviderResponse,
    ProviderFailure,
    ProviderTimeout,
    TransientProviderFailure,
)
from app.core.resources import load_prompt_section
from app.jev.structured_output import (
    MAX_PROVIDER_RESPONSE_BYTES,
    decode_json_object,
    parse_findings,
)
from app.providers.base import ProviderRequest, ProviderResult
from app.providers.catalog import JEV_OUTPUT_TOKENS
from app.providers.retry_after import retry_after_seconds
from app.schemas.jev import Findings


class OpenAIProvider:
    name = "openai"

    def __init__(self, config: Settings, *, transport: httpx.BaseTransport | None = None) -> None:
        if not config.openai_api_key.get_secret_value() or not config.openai_model:
            raise ValueError("OpenAI credentials and model are required")
        self.model = config.openai_model
        self._key = config.openai_api_key
        self._transport = transport
        self._timeout = config.jev_attempt_timeout_seconds
        self._reasoning_effort = config.openai_reasoning_effort
        self._verbosity = config.openai_verbosity
        self.prompt = load_prompt_section("SINGLE")

    def analyze(self, request: ProviderRequest) -> ProviderResult:
        content: list[dict[str, Any]] = [{"type": "input_text", "text": request.form}]
        payload: dict[str, Any] = {
            "model": self.model,
            "instructions": self.prompt,
            "input": [{"role": "user", "content": content}],
            "store": False,
            "stream": False,
            "max_output_tokens": JEV_OUTPUT_TOKENS,
            "text": {
                "format": {
                    "type": "json_schema",
                    "name": "jev_findings",
                    "strict": True,
                    "schema": Findings.model_json_schema(),
                }
            },
        }
        if self._reasoning_effort is not None:
            payload["reasoning"] = {"effort": self._reasoning_effort}
        if self._verbosity is not None:
            payload["text"]["verbosity"] = self._verbosity
        try:
            # Fixed origin, no redirects, no ambient proxies/credentials; one request.
            with httpx.Client(
                transport=self._transport,
                timeout=self._timeout,
                trust_env=False,
                follow_redirects=False,
            ) as client:
                response = client.post(
                    "https://api.openai.com/v1/responses",
                    headers={"Authorization": f"Bearer {self._key.get_secret_value()}"},
                    json=payload,
                )
        except httpx.TimeoutException:
            raise ProviderTimeout() from None
        except httpx.ConnectError:
            raise TransientProviderFailure() from None
        except httpx.HTTPError:
            raise ProviderFailure() from None
        if response.status_code in {408, 504}:
            raise ProviderTimeout()
        temporary = response.status_code in {500, 502, 503}
        if response.status_code == 429:
            try:
                error = response.json()["error"]
                temporary = error.get("type") != "insufficient_quota" and error.get("code") in {
                    "rate_limit_exceeded",
                    "slow_down",
                }
            except (ValueError, KeyError, TypeError, AttributeError):
                temporary = False
        if temporary:
            raise TransientProviderFailure(
                retry_after=retry_after_seconds(response.headers.get("Retry-After"))
            )
        if not 200 <= response.status_code < 300:
            # Never expose/log an upstream error body, which can contain input.
            raise ProviderFailure()
        try:
            if len(response.content) > MAX_PROVIDER_RESPONSE_BYTES:
                raise ValueError("Oversized provider response")
            return self._parse(
                decode_json_object(
                    response.content.decode("utf-8"), max_bytes=MAX_PROVIDER_RESPONSE_BYTES
                )
            )
        except (ValueError, TypeError, KeyError, AttributeError):
            raise InvalidProviderResponse() from None

    @staticmethod
    def _parse(data: Any) -> ProviderResult:
        if (
            not isinstance(data, dict)
            or data.get("status") != "completed"
            or data.get("error") is not None
            or data.get("incomplete_details") is not None
            or not isinstance(data.get("output"), list)
        ):
            raise ValueError("Incomplete response")
        texts = []
        messages = 0
        for output in data["output"]:
            if not isinstance(output, dict):
                raise ValueError("Unexpected output")
            if output.get("type") == "reasoning":
                continue
            if output.get("type") != "message" or output.get("status") != "completed":
                raise ValueError("Unexpected output")
            if output.get("role") != "assistant":
                raise ValueError("Unexpected role")
            messages += 1
            if not isinstance(output.get("content"), list) or len(output["content"]) != 1:
                raise ValueError("Expected one structured output")
            for part in output["content"]:
                if part.get("type") != "output_text":
                    raise ValueError("Refused or unexpected content")
                texts.append(part["text"])
        if len(texts) != 1 or messages != 1:
            raise ValueError("Expected one structured output")
        usage = data.get("usage")
        if usage is None:
            usage = {}
        if not isinstance(usage, dict):
            raise ValueError("Invalid usage metadata")
        return ProviderResult(
            parse_findings(texts[0]),
            usage.get("input_tokens", 0),
            usage.get("output_tokens", 0),
            usage_complete="input_tokens" in usage and "output_tokens" in usage,
        )
