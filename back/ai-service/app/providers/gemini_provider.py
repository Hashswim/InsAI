from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage
from langchain_google_genai import ChatGoogleGenerativeAI

from app.core.config import Settings
from app.core.resources import load_jev_prompt
from app.providers.base import ProviderResult
from app.schemas.jev import Findings, JevRequest


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
            timeout=90,
            max_retries=0,
        )
        self.prompt = load_jev_prompt()

    def analyze(self, request: JevRequest) -> ProviderResult:
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
        result = self.client.with_structured_output(
            Findings, method="json_schema", include_raw=True
        ).invoke([SystemMessage(content=self.prompt), HumanMessage(content=content)])
        if (
            not isinstance(result, dict)
            or result.get("parsing_error")
            or result.get("parsed") is None
        ):
            raise ValueError("Invalid structured model result")
        usage = result["raw"].usage_metadata or {}
        return ProviderResult(
            Findings.model_validate(result["parsed"]),
            usage.get("input_tokens", 0),
            usage.get("output_tokens", 0),
        )
