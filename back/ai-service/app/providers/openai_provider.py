"""Stateless Responses API adapter; no Files API uploads or automatic retries."""

from typing import Any

import httpx

from app.core.config import Settings
from app.core.errors import InvalidProviderResponse, ProviderFailure, ProviderTimeout
from app.core.resources import load_jev_prompt
from app.providers.base import ProviderRequest, ProviderResult
from app.providers.catalog import JEV_OUTPUT_TOKENS
from app.schemas.jev import Findings


class OpenAIProvider:
    name = "openai"

    def __init__(self, config: Settings, *, transport: httpx.BaseTransport | None = None) -> None:
        if not config.openai_api_key.get_secret_value() or not config.openai_model:
            raise ValueError("OpenAI credentials and model are required")
        self.model = config.openai_model
        self._key = config.openai_api_key
        self._transport = transport
        self.prompt = load_jev_prompt()

    def analyze(self, request: ProviderRequest) -> ProviderResult:
        content: list[dict[str, Any]] = []
        for document in request.documents:
            content.extend(
                [
                    {
                        "type": "input_text",
                        "text": f"document_id={document.document_id}; kind={document.kind.value}",
                    },
                    {
                        "type": "input_file",
                        "filename": f"{document.document_id}.pdf",
                        "file_data": f"data:application/pdf;base64,{document.content_base64}",
                    },
                ]
            )
        payload = {
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
        try:
            # Fixed origin, no redirects, no ambient proxies/credentials; one request.
            with httpx.Client(
                transport=self._transport, timeout=90, trust_env=False, follow_redirects=False
            ) as client:
                response = client.post(
                    "https://api.openai.com/v1/responses",
                    headers={"Authorization": f"Bearer {self._key.get_secret_value()}"},
                    json=payload,
                )
        except httpx.TimeoutException:
            raise ProviderTimeout() from None
        except httpx.HTTPError:
            raise ProviderFailure() from None
        if response.status_code in {408, 504}:
            raise ProviderTimeout()
        if not 200 <= response.status_code < 300:
            # Never expose/log an upstream error body, which can contain input.
            raise ProviderFailure()
        try:
            return self._parse(response.json())
        except (ValueError, TypeError, KeyError, AttributeError):
            raise InvalidProviderResponse() from None

    @staticmethod
    def _parse(data: Any) -> ProviderResult:
        if data.get("status") != "completed" or data.get("error"):
            raise ValueError("Incomplete response")
        texts = []
        for output in data["output"]:
            if output.get("type") == "reasoning":
                continue
            if output.get("type") != "message" or output.get("status") != "completed":
                raise ValueError("Unexpected output")
            if output.get("role") != "assistant":
                raise ValueError("Unexpected role")
            for part in output["content"]:
                if part.get("type") != "output_text":
                    raise ValueError("Refused or unexpected content")
                texts.append(part["text"])
        if len(texts) != 1:
            raise ValueError("Expected one structured output")
        usage = data.get("usage") or {}
        return ProviderResult(
            Findings.model_validate_json(texts[0]),
            usage.get("input_tokens", 0),
            usage.get("output_tokens", 0),
        )
