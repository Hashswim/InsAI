from unittest.mock import Mock

from conftest import FakeProvider

from app.core.config import Settings
from app.providers.gemini_provider import GeminiProvider


def test_pdf_and_schema_are_passed_to_langchain(monkeypatch, sample_request):
    structured = Mock()
    client = Mock()
    client.with_structured_output.return_value = structured
    structured.invoke.return_value = {
        "parsed": FakeProvider().analyze(sample_request).findings,
        "raw": Mock(usage_metadata={"input_tokens": 12, "output_tokens": 34}),
        "parsing_error": None,
    }
    monkeypatch.setattr(
        "app.providers.gemini_provider.ChatGoogleGenerativeAI", Mock(return_value=client)
    )
    provider = GeminiProvider(Settings(google_api_key="test-key", gemini_model="test-model"))
    result = provider.analyze(sample_request)
    assert result.input_tokens == 12
    messages = structured.invoke.call_args.args[0]
    files = [block for block in messages[1].content if block["type"] == "file"]
    assert len(files) == 3
    assert all(block["mime_type"] == "application/pdf" for block in files)
    assert client.with_structured_output.call_args.kwargs["method"] == "json_schema"
