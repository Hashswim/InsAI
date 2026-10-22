import json
import logging
from unittest.mock import Mock

import httpx
import pytest
from conftest import FakeProvider
from fastapi.testclient import TestClient
from langchain_google_genai import ChatGoogleGenerativeAI

from app.api.jev import service
from app.application.jev_service import JevService
from app.core.config import Settings
from app.core.errors import (
    InvalidProviderResponse,
    ProviderFailure,
    ProviderTimeout,
    TransientProviderFailure,
)
from app.core.logging import install_secret_redaction
from app.main import app
from app.providers.gemini_provider import GeminiProvider


def real_sdk_service(monkeypatch, handler):
    """Keep the real LangChain/Google conversion, replace only HTTP transport."""

    def factory(**options):
        options["client_args"]["transport"] = httpx.MockTransport(handler)
        return ChatGoogleGenerativeAI(**options)

    monkeypatch.setattr("app.providers.gemini_provider.ChatGoogleGenerativeAI", factory)
    monkeypatch.setenv("GOOGLE_GENAI_USE_VERTEXAI", "true")
    config = Settings(google_api_key="synthetic-google-key", gemini_model="fake-model")
    provider = GeminiProvider(config)
    trace = Mock()
    trace.record.return_value = (None, "DISABLED")
    return JevService(provider, trace)


def successful_response(request, finish="STOP"):
    return {
        "candidates": [
            {
                "content": {
                    "role": "model",
                    "parts": [{"text": FakeProvider().analyze(request).findings.model_dump_json()}],
                },
                "finishReason": finish,
            }
        ],
        "usageMetadata": {
            "promptTokenCount": 12,
            "candidatesTokenCount": 34,
            "totalTokenCount": 46,
        },
    }


def test_real_sdk_text_form_and_api_contract(monkeypatch, sample_request):
    calls = []

    def handler(request):
        calls.append(request)
        return httpx.Response(200, json=successful_response(sample_request))

    adapter_service = real_sdk_service(monkeypatch, handler)
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "synthetic-service-token")
    app.dependency_overrides[service] = lambda: adapter_service
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev",
            headers={"Authorization": "Bearer synthetic-service-token"},
            json=sample_request.model_dump(mode="json"),
        )
    assert response.status_code == 200
    data = response.json()
    assert data["provider"] == "gemini"
    assert data["model"] == "fake-model"
    assert data["input_tokens"] == 12 and data["output_tokens"] == 34
    assert data["submission_status"] == "ON_HOLD"
    assert data["trace_status"] == "DISABLED" and data["trace_id"] is None
    assert len(calls) == 1
    sent = calls[0]
    assert sent.url.host == "generativelanguage.googleapis.com"
    assert sent.url.path.endswith("/models/fake-model:generateContent")
    assert sent.headers["x-goog-api-key"] == "synthetic-google-key"
    body = json.loads(sent.content)
    parts = body["contents"][0]["parts"]
    # The wire request carries the extracted text form only; never a PDF.
    assert not [p for p in parts if "inlineData" in p or "fileData" in p]
    assert [p["text"][:14] for p in parts] == ["JEV_TEXT_FORM="]
    assert "%PDF" not in sent.content.decode()
    for document in sample_request.documents:
        assert document.content_base64 not in sent.content.decode()
    assert body["generationConfig"]["responseMimeType"] == "application/json"
    assert body["generationConfig"]["maxOutputTokens"] == 16000
    assert body["systemInstruction"]["parts"][0]["text"]
    assert sample_request.documents[0].content_base64 not in str(adapter_service.trace.mock_calls)


@pytest.mark.parametrize(
    "status,expected",
    [
        (401, ProviderFailure),
        (429, ProviderFailure),
        (503, TransientProviderFailure),
        (504, ProviderTimeout),
    ],
)
def test_real_sdk_makes_one_attempt_and_sanitizes_errors(
    monkeypatch, sample_request, status, expected
):
    calls = []

    def handler(request):
        calls.append(request)
        return httpx.Response(status, json={"error": {"code": status, "message": "SECRET_PATIENT"}})

    with pytest.raises(expected) as error:
        real_sdk_service(monkeypatch, handler).run(sample_request)
    assert len(calls) == 1
    assert "SECRET_PATIENT" not in str(error.value)


@pytest.mark.parametrize("finish", ["MAX_TOKENS", "SAFETY", "RECITATION"])
def test_real_sdk_partial_output_is_not_success(monkeypatch, sample_request, finish):
    def handler(request):
        return httpx.Response(200, json=successful_response(sample_request, finish))

    with pytest.raises(InvalidProviderResponse):
        real_sdk_service(monkeypatch, handler).run(sample_request)


@pytest.mark.parametrize("mutation", ["duplicate", "fence", "numeric_string", "truncated"])
def test_real_sdk_parsing_does_not_bypass_raw_validation(
    monkeypatch, sample_request, mutation, caplog
):
    calls = []

    def handler(request):
        calls.append(request)
        data = successful_response(sample_request)
        part = data["candidates"][0]["content"]["parts"][0]
        text = part["text"]
        if mutation == "duplicate":
            text = text.replace('"page":1', '"page":2,"page":1')
        elif mutation == "fence":
            text = "```json\n" + text + "\n```"
        elif mutation == "numeric_string":
            text = text.replace('"confidence":0.8', '"confidence":"0.8"')
        elif mutation == "truncated":
            text = text[:-2]
        part["text"] = text
        return httpx.Response(200, json=data)

    with pytest.raises(InvalidProviderResponse):
        real_sdk_service(monkeypatch, handler).run(sample_request)
    assert len(calls) == 1
    assert "날짜 항목 확인" not in caplog.text


@pytest.mark.parametrize(
    "logger_name", ["langchain_google_genai.chat_models", "google.genai.models"]
)
def test_sdk_diagnostics_do_not_log_raw_feedback(caplog, logger_name):
    restore = install_secret_redaction(Settings())
    try:
        with caplog.at_level(logging.WARNING):
            try:
                raise ValueError("SECRET_PATIENT")
            except ValueError:
                logging.getLogger(logger_name).exception("raw feedback=%s", "SECRET_PATIENT")
        assert "SECRET_PATIENT" not in caplog.text
        assert "Provider SDK diagnostic suppressed" in caplog.text
    finally:
        restore()
