from typing import Any

import httpx
from google.genai.errors import APIError
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_google_genai import ChatGoogleGenerativeAI

from app.chains.structured_output import parse_findings, validate_findings
from app.core.config import Settings
from app.core.errors import (
    InvalidProviderResponse,
    ProviderFailure,
    ProviderTimeout,
    TransientProviderFailure,
)
from app.core.resources import load_jev_prompt
from app.providers.base import ProviderRequest, ProviderResult
from app.providers.catalog import JEV_OUTPUT_TOKENS
from app.providers.retry_after import retry_after_seconds
from app.schemas.jev import Findings


class GeminiProvider:
    name = "gemini"

    def __init__(self, config: Settings) -> None:
        if not config.google_api_key.get_secret_value() or not config.gemini_model:
            raise ValueError("Gemini credentials and model are required")
        self.model = config.gemini_model
        self.client = ChatGoogleGenerativeAI(
            model=self.model,
            api_key=config.google_api_key.get_secret_value(),
            temperature=0,
            timeout=config.jev_attempt_timeout_seconds,
            # Google/LangChain counts total attempts here, not extra attempts.
            max_retries=1,
            max_output_tokens=JEV_OUTPUT_TOKENS,
            vertexai=False,
            base_url="https://generativelanguage.googleapis.com",
            client_args={"trust_env": False, "follow_redirects": False},
        )
        self.prompt = load_jev_prompt()

    def analyze(self, request: ProviderRequest) -> ProviderResult:
        content: list[str | dict[str, Any]] = []
        for document in request.documents:
            content.extend(
                [
                    {
                        "type": "text",
                        "text": f"document_id={document.document_id}; kind={document.kind.value}",
                    },
                    {
                        "type": "file",
                        "source_type": "base64",
                        "mime_type": "application/pdf",
                        "data": document.content_base64,
                    },
                ]
            )
        try:
            result = self.client.with_structured_output(
                Findings, method="json_schema", include_raw=True
            ).invoke([SystemMessage(content=self.prompt), HumanMessage(content=content)])
        except (TimeoutError, httpx.TimeoutException):
            raise ProviderTimeout() from None
        except httpx.ConnectError:
            raise TransientProviderFailure() from None
        except APIError as error:
            if error.code in {408, 504}:
                raise ProviderTimeout() from None
            if error.code in {500, 502, 503}:
                headers = getattr(error.response, "headers", {})
                raise TransientProviderFailure(
                    retry_after=retry_after_seconds(headers.get("Retry-After"))
                ) from None
            raise ProviderFailure() from None
        except ValueError:
            raise InvalidProviderResponse() from None
        except Exception:
            # SDK errors may embed raw inputs; never propagate their text or cause.
            raise ProviderFailure() from None
        try:
            return self._parse(result)
        except (ValueError, TypeError, KeyError, AttributeError):
            raise InvalidProviderResponse() from None

    @staticmethod
    def _parse(result: Any) -> ProviderResult:
        if (
            not isinstance(result, dict)
            or result.get("parsing_error") is not None
            or result.get("parsed") is None
        ):
            raise ValueError("Invalid structured model result")
        raw = result.get("raw")
        if (
            not isinstance(raw, AIMessage)
            or not raw.content
            or raw.tool_calls
            or raw.invalid_tool_calls
            or raw.additional_kwargs.get("refusal")
        ):
            raise ValueError("Invalid raw model result")
        metadata = raw.response_metadata
        if metadata.get("finish_reason") != "STOP":
            raise ValueError("Model output did not finish normally")
        feedback = metadata.get("prompt_feedback") or {}
        if feedback.get("block_reason") not in {None, 0, "BLOCK_REASON_UNSPECIFIED"}:
            raise ValueError("Model input was blocked")
        usage: Any = raw.usage_metadata if raw.usage_metadata is not None else {}
        if not isinstance(usage, dict):
            raise ValueError("Invalid usage metadata")
        content = raw.content
        if isinstance(content, list):
            if (
                len(content) != 1
                or not isinstance(content[0], dict)
                or content[0].get("type") != "text"
            ):
                raise ValueError("Expected one structured output")
            text = content[0].get("text")
        else:
            text = content
        findings = parse_findings(text)
        if findings != validate_findings(result["parsed"]):
            raise ValueError("SDK parsed output does not match raw output")
        return ProviderResult(
            findings,
            usage.get("input_tokens", 0),
            usage.get("output_tokens", 0),
            usage_complete="input_tokens" in usage and "output_tokens" in usage,
        )
