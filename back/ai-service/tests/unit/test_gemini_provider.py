import json
from unittest.mock import Mock

import httpx
import pytest
from conftest import FakeProvider
from google.genai.errors import ClientError, ServerError
from langchain_core.messages import AIMessage

from app.core.config import Settings
from app.core.errors import (
    InvalidProviderResponse,
    ProviderFailure,
    ProviderTimeout,
    TransientProviderFailure,
)
from app.core.resources import load_jev_prompt
from app.providers.base import Provider, ProviderDocument, ProviderRequest
from app.providers.gemini_provider import GeminiProvider


def test_pdf_and_schema_are_passed_to_langchain(monkeypatch, sample_request):
    structured = Mock()
    client = Mock()
    client.with_structured_output.return_value = structured
    structured.invoke.return_value = {
        "parsed": FakeProvider().analyze(sample_request).findings,
        "raw": AIMessage(
            content=FakeProvider().analyze(sample_request).findings.model_dump_json(),
            response_metadata={"finish_reason": "STOP"},
            usage_metadata={"input_tokens": 12, "output_tokens": 34, "total_tokens": 46},
        ),
        "parsing_error": None,
    }
    monkeypatch.setattr(
        "app.providers.gemini_provider.ChatGoogleGenerativeAI", Mock(return_value=client)
    )
    provider = GeminiProvider(Settings(google_api_key="test-key", gemini_model="test-model"))
    port: Provider = provider
    result = port.analyze(
        ProviderRequest(
            sample_request.request_id,
            tuple(
                ProviderDocument(d.document_id, d.kind, d.content_base64)
                for d in sample_request.documents
            ),
        )
    )
    assert result.input_tokens == 12
    messages = structured.invoke.call_args.args[0]
    assert messages[0].content == load_jev_prompt()
    files = [block for block in messages[1].content if block["type"] == "file"]
    assert len(files) == 3
    assert all(block["mime_type"] == "application/pdf" for block in files)
    assert client.with_structured_output.call_args.kwargs["method"] == "json_schema"


def port_request(request):
    return ProviderRequest(
        request.request_id,
        tuple(ProviderDocument(d.document_id, d.kind, d.content_base64) for d in request.documents),
    )


def model_result(request):
    findings = FakeProvider().analyze(request).findings
    return {
        "parsed": findings,
        "raw": AIMessage(
            content=findings.model_dump_json(),
            response_metadata={"finish_reason": "STOP", "prompt_feedback": {"block_reason": 0}},
            usage_metadata={"input_tokens": 10, "output_tokens": 20, "total_tokens": 30},
        ),
        "parsing_error": None,
    }


def mocked_provider(monkeypatch, *, result=None, error=None):
    structured = Mock()
    structured.invoke.return_value = result
    structured.invoke.side_effect = error
    client = Mock()
    client.with_structured_output.return_value = structured
    factory = Mock(return_value=client)
    monkeypatch.setattr("app.providers.gemini_provider.ChatGoogleGenerativeAI", factory)
    provider = GeminiProvider(Settings(google_api_key="synthetic-key", gemini_model="fake-model"))
    return provider, structured, factory


def test_explicit_backend_endpoint_and_attempts(monkeypatch):
    _, _, factory = mocked_provider(monkeypatch)
    options = factory.call_args.kwargs
    assert options["vertexai"] is False
    assert options["max_retries"] == 1
    assert options["timeout"] == 90
    assert options["max_output_tokens"] == 16000
    assert options["client_args"] == {"trust_env": False, "follow_redirects": False}
    assert options["base_url"] == "https://generativelanguage.googleapis.com"


@pytest.mark.parametrize(
    "error,expected",
    [
        (TimeoutError("SECRET"), ProviderTimeout),
        (httpx.ReadTimeout("SECRET"), ProviderTimeout),
        (httpx.ConnectError("SECRET"), ProviderFailure),
        (ClientError(401, {"error": {"message": "SECRET"}}), ProviderFailure),
        (ClientError(429, {"error": {"message": "SECRET"}}), ProviderFailure),
        (ClientError(408, {"error": {"message": "SECRET"}}), ProviderTimeout),
        (ServerError(504, {"error": {"message": "SECRET"}}), ProviderTimeout),
        (ServerError(503, {"error": {"message": "SECRET"}}), ProviderFailure),
        (RuntimeError("SECRET"), ProviderFailure),
        (ValueError("SECRET"), InvalidProviderResponse),
    ],
)
def test_sdk_error_is_portable_and_safe(monkeypatch, sample_request, error, expected):
    provider, structured, _ = mocked_provider(monkeypatch, error=error)
    with pytest.raises(expected) as caught:
        provider.analyze(port_request(sample_request))
    assert "SECRET" not in str(caught.value)
    assert caught.value.__suppress_context__
    structured.invoke.assert_called_once()


@pytest.mark.parametrize(
    "mutation",
    [
        "nondict",
        "parse_error",
        "missing_parsed",
        "missing_raw",
        "wrong_raw",
        "empty_content",
        "max_tokens",
        "safety",
        "missing_finish",
        "blocked",
        "bad_schema",
        "bad_usage",
        "negative_usage",
        "bool_usage",
        "tool_call",
    ],
)
def test_invalid_or_partial_result_is_rejected(monkeypatch, sample_request, mutation):
    result = model_result(sample_request)
    raw = result["raw"]
    if mutation == "nondict":
        result = []
    elif mutation == "parse_error":
        result["parsing_error"] = ValueError("SECRET")
    elif mutation == "missing_parsed":
        result["parsed"] = None
    elif mutation == "missing_raw":
        result.pop("raw")
    elif mutation == "wrong_raw":
        result["raw"] = {"content": "SECRET"}
    elif mutation == "empty_content":
        raw.content = ""
    elif mutation in {"max_tokens", "safety", "missing_finish"}:
        raw.response_metadata = {
            "finish_reason": {
                "max_tokens": "MAX_TOKENS",
                "safety": "SAFETY",
                "missing_finish": None,
            }[mutation]
        }
    elif mutation == "blocked":
        raw.response_metadata["prompt_feedback"] = {"block_reason": 1}
    elif mutation == "bad_schema":
        result["parsed"] = {"items": []}
    elif mutation == "bad_usage":
        raw.usage_metadata = "SECRET"
    elif mutation == "negative_usage":
        raw.usage_metadata["input_tokens"] = -1
    elif mutation == "bool_usage":
        raw.usage_metadata["output_tokens"] = True
    elif mutation == "tool_call":
        raw.tool_calls = [{"name": "unexpected", "args": {}, "id": "call"}]
    provider, _, _ = mocked_provider(monkeypatch, result=result)
    with pytest.raises(InvalidProviderResponse):
        provider.analyze(port_request(sample_request))


def test_missing_usage_and_dictionary_output(monkeypatch, sample_request):
    result = model_result(sample_request)
    result["raw"].usage_metadata = None
    result["parsed"] = result["parsed"].model_dump()
    provider, _, _ = mocked_provider(monkeypatch, result=result)
    parsed = provider.analyze(port_request(sample_request))
    assert parsed.input_tokens == parsed.output_tokens == 0
    assert len(parsed.findings.items) == 1


@pytest.mark.parametrize(
    "mutation",
    [
        "fence",
        "duplicate_key",
        "numeric_string",
        "mismatch",
        "mixed_blocks",
        "invalid_tool",
        "refusal",
        "bad_metadata",
        "bad_feedback",
    ],
)
def test_raw_output_cannot_be_repaired_or_overridden_by_sdk(monkeypatch, sample_request, mutation):
    result = model_result(sample_request)
    raw = result["raw"]
    if mutation == "fence":
        raw.content = f"```json\n{raw.content}\n```"
    elif mutation == "duplicate_key":
        raw.content = raw.content.replace('"page":1', '"page":2,"page":1')
    elif mutation == "numeric_string":
        raw.content = raw.content.replace('"confidence":0.8', '"confidence":"0.8"')
    elif mutation == "mismatch":
        data = json.loads(raw.content)
        data["items"][0]["item"] = "different raw item"
        raw.content = json.dumps(data)
    elif mutation == "mixed_blocks":
        raw.content = [{"type": "text", "text": raw.content}, {"type": "image", "data": "SECRET"}]
    elif mutation == "invalid_tool":
        raw.invalid_tool_calls = [{"name": "unexpected", "args": "SECRET", "id": "call"}]
    elif mutation == "refusal":
        raw.additional_kwargs["refusal"] = "SECRET_PATIENT"
    elif mutation == "bad_metadata":
        raw.response_metadata = []
    elif mutation == "bad_feedback":
        raw.response_metadata["prompt_feedback"] = "SECRET_PATIENT"
    provider, structured, _ = mocked_provider(monkeypatch, result=result)
    with pytest.raises(InvalidProviderResponse) as error:
        provider.analyze(port_request(sample_request))
    assert "SECRET" not in str(error.value)
    structured.invoke.assert_called_once()


def test_single_raw_text_block_is_supported(monkeypatch, sample_request):
    result = model_result(sample_request)
    result["raw"].content = [{"type": "text", "text": result["raw"].content}]
    provider, _, _ = mocked_provider(monkeypatch, result=result)
    assert len(provider.analyze(port_request(sample_request)).findings.items) == 1


@pytest.mark.parametrize("changes", [{}, {"google_api_key": "synthetic"}, {"gemini_model": "fake"}])
def test_constructor_requires_key_and_model(changes):
    with pytest.raises(ValueError, match="credentials and model"):
        GeminiProvider(Settings(**changes))


def test_overload_retains_only_retry_delay(monkeypatch, sample_request):
    error = ServerError(
        503,
        {"error": {"message": "SECRET_PATIENT"}},
        response=httpx.Response(503, headers={"Retry-After": "60"}),
    )
    provider, _, _ = mocked_provider(monkeypatch, error=error)
    with pytest.raises(TransientProviderFailure) as caught:
        provider.analyze(port_request(sample_request))
    assert caught.value.retry_after == 60
    assert "SECRET_PATIENT" not in str(caught.value)
