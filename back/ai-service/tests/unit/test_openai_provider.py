import json
from copy import deepcopy
from unittest.mock import Mock

import httpx
import pytest
from conftest import FakeProvider
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.api.jev import service
from app.application.jev_service import JevService
from app.chains.jev_workflow import run_workflow
from app.core.config import Settings
from app.core.errors import (
    InvalidProviderResponse,
    ProviderFailure,
    ProviderTimeout,
    TransientProviderFailure,
)
from app.core.logging import install_secret_redaction
from app.main import app
from app.providers.base import Provider
from app.providers.openai_provider import OpenAIProvider
from app.providers.router import build_provider


def config(**changes):
    return Settings(
        **{
            "app_env": "local",
            "jev_provider": "openai",
            "jev_service_token": "synthetic-service-token",
            "openai_api_key": "SENTINEL_OPENAI_KEY",
            "openai_model": "fake-openai-model",
            **changes,
        }
    )


def response_data(request):
    return {
        "status": "completed",
        "output": [
            {"type": "reasoning", "summary": []},
            {
                "type": "message",
                "role": "assistant",
                "status": "completed",
                "content": [
                    {
                        "type": "output_text",
                        "text": FakeProvider().analyze(request).findings.model_dump_json(),
                    }
                ],
            },
        ],
        "usage": {"input_tokens": 123, "output_tokens": 45},
    }


def service_for(handler):
    provider = OpenAIProvider(config(), transport=httpx.MockTransport(handler))
    trace = Mock()
    trace.record.return_value = (None, "DISABLED")
    return JevService(provider, trace)


def test_pdf_schema_and_stateless_single_request(sample_request):
    calls = []

    def handler(request):
        calls.append(request)
        assert str(request.url) == "https://api.openai.com/v1/responses"
        assert request.headers["authorization"] == "Bearer SENTINEL_OPENAI_KEY"
        body = json.loads(request.content)
        assert body["store"] is body["stream"] is False
        assert body["model"] == "fake-openai-model"
        assert body["text"]["format"]["strict"] is True
        assert body["text"]["format"]["schema"]["additionalProperties"] is False
        files = [p for p in body["input"][0]["content"] if p["type"] == "input_file"]
        assert len(files) == 3
        for file, document in zip(files, sample_request.documents, strict=True):
            assert file["file_data"] == f"data:application/pdf;base64,{document.content_base64}"
            assert file["filename"] == f"{document.document_id}.pdf"
        assert "SENTINEL_OPENAI_KEY" not in request.content.decode()
        return httpx.Response(200, json=response_data(sample_request))

    service = service_for(handler)
    result = service.run(sample_request)
    assert len(calls) == 1
    assert result.provider == "openai"
    assert result.input_tokens == 123 and result.output_tokens == 45
    assert result.submission_status == "ON_HOLD"
    assert result.regulations_status == "NOT_REGISTERED"
    assert sample_request.documents[0].content_base64 not in str(service.trace.mock_calls)


@pytest.mark.parametrize("status", [301, 400, 401, 403, 429, 500, 503])
def test_http_errors_are_safe_and_not_retried(sample_request, status):
    calls = []

    def handler(request):
        calls.append(request)
        return httpx.Response(
            status, text="SECRET_PATIENT", headers={"Location": "https://evil.test"}
        )

    with pytest.raises(ProviderFailure) as error:
        service_for(handler).run(sample_request)
    assert len(calls) == 1
    assert "SECRET_PATIENT" not in str(error.value)


@pytest.mark.parametrize("failure", ["timeout", "connection", 408, 504])
def test_transport_errors_are_normalized(sample_request, failure):
    def handler(request):
        if failure == "timeout":
            raise httpx.ReadTimeout("SECRET_PATIENT")
        if failure == "connection":
            raise httpx.ConnectError("SECRET_PATIENT")
        return httpx.Response(failure)

    expected = ProviderFailure if failure == "connection" else ProviderTimeout
    with pytest.raises(expected) as error:
        service_for(handler).run(sample_request)
    assert "SECRET_PATIENT" not in str(error.value)


@pytest.mark.parametrize(
    "mutation",
    [
        "incomplete",
        "refusal",
        "empty",
        "bad_json",
        "bad_schema",
        "negative_usage",
        "bool_usage",
        "duplicate",
        "unknown_document",
        "unknown_output",
        "nonobject",
    ],
)
def test_invalid_results_fail_closed(sample_request, mutation):
    data = response_data(sample_request)
    content = data["output"][1]["content"]
    if mutation == "incomplete":
        data["status"] = "incomplete"
    elif mutation == "refusal":
        content[0] = {"type": "refusal", "refusal": "SECRET_PATIENT"}
    elif mutation == "empty":
        data["output"] = []
    elif mutation == "bad_json":
        content[0]["text"] = "SECRET_PATIENT"
    elif mutation == "bad_schema":
        content[0]["text"] = '{"items":[]}'
    elif mutation == "negative_usage":
        data["usage"]["input_tokens"] = -1
    elif mutation == "bool_usage":
        data["usage"]["output_tokens"] = True
    elif mutation == "duplicate":
        content.append(deepcopy(content[0]))
    elif mutation == "unknown_document":
        findings = json.loads(content[0]["text"])
        findings["items"][0]["evidence"][0]["page"] = 99
        content[0]["text"] = json.dumps(findings)
    elif mutation == "unknown_output":
        data["output"].append({"type": "function_call"})
    elif mutation == "nonobject":
        data = []
    with pytest.raises(InvalidProviderResponse):
        service_for(lambda request: httpx.Response(200, json=data)).run(sample_request)


def test_non_json_response_and_missing_usage(sample_request):
    with pytest.raises(InvalidProviderResponse):
        service_for(lambda request: httpx.Response(200, text="SECRET")).run(sample_request)
    data = response_data(sample_request)
    data.pop("usage")
    result = service_for(lambda request: httpx.Response(200, json=data)).run(sample_request)
    assert result.input_tokens == result.output_tokens == 0


@pytest.mark.parametrize("missing", ["openai_api_key", "openai_model", "jev_service_token"])
def test_credentials_are_required(missing):
    with pytest.raises(ValidationError):
        config(**{missing: ""})


def test_local_selection_and_production_guard(catalog_entry, catalog_path):
    path = catalog_path([catalog_entry("openai", "fake-openai-model")])
    provider: Provider = build_provider(config(jev_model_catalog_path=path))
    assert isinstance(provider, OpenAIProvider)
    with pytest.raises(ValidationError):
        config(app_env="production", jev_service_token="x" * 32)
    with pytest.raises(ValidationError):
        config(jev_service_token="SENTINEL_OPENAI_KEY")
    with pytest.raises(ValidationError):
        config(openai_api_key="invalid key")
    assert "SENTINEL" not in repr(config())
    assert "SENTINEL" not in config().model_dump_json()


def test_openai_key_is_redacted(caplog):
    import logging

    restore = install_secret_redaction(config())
    try:
        with caplog.at_level(logging.WARNING):
            logging.getLogger("openai.test").warning("key=%s", "SENTINEL_OPENAI_KEY")
        assert "SENTINEL_OPENAI_KEY" not in caplog.text
    finally:
        restore()


def test_internal_api_returns_openai_contract(monkeypatch, sample_request):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "synthetic-service-token")
    adapter_service = service_for(
        lambda request: httpx.Response(200, json=response_data(sample_request))
    )
    app.dependency_overrides[service] = lambda: adapter_service
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev",
            headers={"Authorization": "Bearer synthetic-service-token"},
            json=sample_request.model_dump(mode="json"),
        )
    assert response.status_code == 200
    body = response.json()
    assert body["provider"] == "openai"
    assert body["request_id"] == str(sample_request.request_id)
    assert body["submission_status"] == "ON_HOLD"
    assert body["trace_id"] is None and body["trace_status"] == "DISABLED"


@pytest.mark.parametrize(
    "code,error_type,temporary",
    [
        ("rate_limit_exceeded", "rate_limit_error", True),
        ("slow_down", "rate_limit_error", True),
        ("insufficient_quota", "insufficient_quota", False),
        ("organization_spend_limit_exceeded", "insufficient_quota", False),
        ("project_spend_limit_exceeded", "insufficient_quota", False),
        ("unknown", "unknown", False),
    ],
)
def test_rate_limit_and_quota_are_distinct(sample_request, code, error_type, temporary):
    def handler(request):
        return httpx.Response(
            429,
            json={"error": {"code": code, "type": error_type, "message": "SECRET_PATIENT"}},
            headers={"Retry-After": "1.5"},
        )

    provider = OpenAIProvider(config(), transport=httpx.MockTransport(handler))
    with pytest.raises(ProviderFailure) as caught:
        run_workflow(sample_request, provider)
    assert isinstance(caught.value, TransientProviderFailure) is temporary
    if temporary:
        assert caught.value.retry_after == 1.5
    assert "SECRET_PATIENT" not in str(caught.value)


def test_overload_retains_only_numeric_retry_delay(sample_request):
    provider = OpenAIProvider(
        config(),
        transport=httpx.MockTransport(
            lambda request: httpx.Response(
                503, text="SECRET_PATIENT", headers={"Retry-After": "60"}
            )
        ),
    )
    with pytest.raises(TransientProviderFailure) as caught:
        run_workflow(sample_request, provider)
    assert caught.value.retry_after == 60
