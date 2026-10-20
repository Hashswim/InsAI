from dataclasses import replace
from datetime import datetime, timezone
from unittest.mock import Mock

import pytest
from conftest import FakeProvider
from fastapi import Request
from fastapi.testclient import TestClient

from app.api.middleware import http_context
from app.core.errors import TransientProviderFailure
from app.core.usage import usage_guard
from app.main import app


def test_authentication_precedes_request_quota_and_health_is_exempt(monkeypatch):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "synthetic-token")
    monkeypatch.setenv("JEV_REQUESTS_PER_MINUTE", "1")
    with TestClient(app) as client:
        denied = client.post(
            "/internal/jev", content=b"SECRET", headers={"Authorization": "Bearer wrong"}
        )
        assert denied.status_code == 401
        invalid = client.post(
            "/internal/jev",
            content=b"{",
            headers={"Authorization": "Bearer synthetic-token", "Content-Type": "application/json"},
        )
        assert invalid.status_code == 422
        limited = client.post(
            "/internal/jev", content=b"SECRET", headers={"Authorization": "Bearer synthetic-token"}
        )
        assert limited.status_code == 429
        assert limited.json()["error"]["code"] == "RATE_LIMITED"
        assert int(limited.headers["Retry-After"]) >= 1
        assert "SECRET" not in limited.text
        assert client.get("/health/live").status_code == 200


@pytest.mark.anyio
async def test_request_limit_rejects_before_body_is_read(monkeypatch):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "synthetic-token")
    monkeypatch.setenv("JEV_REQUESTS_PER_MINUTE", "1")
    guard = usage_guard()
    guard.release(guard.admit())

    async def receive():
        raise AssertionError("Rejected request body must not be read")

    request = Request(
        {
            "type": "http",
            "method": "POST",
            "path": "/internal/jev",
            "headers": [(b"authorization", b"Bearer synthetic-token")],
            "query_string": b"",
            "scheme": "http",
            "server": ("test", 80),
        },
        receive,
    )

    async def next_handler(request):
        raise AssertionError("Request handler must not run")

    assert (await http_context(request, next_handler)).status_code == 429


@pytest.fixture
def anyio_backend():
    return "asyncio"


def setup_model(monkeypatch, path, **changes):
    for key, value in {
        "APP_ENV": "local",
        "JEV_PROVIDER": "gemini",
        "GOOGLE_API_KEY": "synthetic-key",
        "GEMINI_MODEL": "priced-model",
        "JEV_SERVICE_TOKEN": "synthetic-token",
        "JEV_MODEL_CATALOG_PATH": str(path),
        "LANGSMITH_ENABLED": "true",
        "LANGSMITH_API_KEY": "synthetic-trace",
        **changes,
    }.items():
        monkeypatch.setenv(key, value)


def priced_entry(catalog_entry):
    return catalog_entry(
        model="priced-model",
        context_window_tokens=20000,
        pricing={
            "input_usd_per_million": "1",
            "output_usd_per_million": "2",
            "reviewed_on": datetime.now(timezone.utc).date().isoformat(),
            "source": "synthetic not real pricing",
        },
    )


def post(client, sample_request):
    return client.post(
        "/internal/jev",
        json=sample_request.model_dump(mode="json"),
        headers={"Authorization": "Bearer synthetic-token"},
    )


def test_budget_rejects_before_inference_and_never_falls_back(
    monkeypatch, catalog_entry, catalog_path, sample_request
):
    setup_model(
        monkeypatch,
        catalog_path([priced_entry(catalog_entry)]),
        JEV_COST_GUARD_ENABLED="true",
        JEV_REQUEST_BUDGET_USD="0.01",
        JEV_DAILY_BUDGET_USD="1",
    )
    provider = FakeProvider()
    provider.model = "priced-model"
    provider.analyze = Mock(side_effect=AssertionError("Model must not run over budget"))
    monkeypatch.setattr("app.providers.router.GeminiProvider", Mock(return_value=provider))
    trace = Mock()
    monkeypatch.setattr("langsmith.Client", Mock(return_value=trace))
    with TestClient(app) as client:
        response = post(client, sample_request)
    assert response.status_code == 429
    assert "Retry-After" not in response.headers
    metadata = trace.create_run.call_args.kwargs["extra"]["metadata"]
    assert metadata["execution_attempts"] == 0
    provider.analyze.assert_not_called()


def test_known_usage_reconciles_and_exports_only_estimate(
    monkeypatch, catalog_entry, catalog_path, sample_request
):
    setup_model(
        monkeypatch,
        catalog_path([priced_entry(catalog_entry)]),
        JEV_COST_GUARD_ENABLED="true",
        JEV_REQUEST_BUDGET_USD="0.1",
        JEV_DAILY_BUDGET_USD="1",
    )
    provider = FakeProvider()
    provider.model = "priced-model"
    provider.analyze = Mock(
        return_value=replace(FakeProvider().analyze(sample_request), usage_complete=True)
    )
    monkeypatch.setattr("app.providers.router.GeminiProvider", Mock(return_value=provider))
    trace = Mock()
    monkeypatch.setattr("langsmith.Client", Mock(return_value=trace))
    with TestClient(app) as client:
        response = post(client, sample_request)
    assert response.status_code == 200
    metadata = trace.create_run.call_args.kwargs["extra"]["metadata"]
    assert metadata["cost_estimate_usd"] == "0.000050000"
    assert metadata["cost_accounting"] == "USAGE_ESTIMATE"
    assert "synthetic not real pricing" not in str(trace.mock_calls)
    assert "content_base64" not in str(trace.mock_calls)


def test_failed_retry_cannot_bypass_request_budget(
    monkeypatch, catalog_entry, catalog_path, sample_request
):
    setup_model(
        monkeypatch,
        catalog_path([priced_entry(catalog_entry)]),
        JEV_COST_GUARD_ENABLED="true",
        JEV_REQUEST_BUDGET_USD="0.1",
        JEV_DAILY_BUDGET_USD="1",
        JEV_MAX_ATTEMPTS="2",
        JEV_RETRY_ATTEMPTS="2",
        JEV_RETRY_BASE_DELAY_SECONDS="0",
    )
    provider = FakeProvider()
    provider.model = "priced-model"
    provider.analyze = Mock(side_effect=TransientProviderFailure())
    monkeypatch.setattr("app.providers.router.GeminiProvider", Mock(return_value=provider))
    trace = Mock()
    monkeypatch.setattr("langsmith.Client", Mock(return_value=trace))
    with TestClient(app) as client:
        response = post(client, sample_request)
    assert response.status_code == 429
    metadata = trace.create_run.call_args.kwargs["extra"]["metadata"]
    assert metadata["execution_attempts"] == 1
    assert metadata["cost_estimate_usd"] == "0.052000000"
    assert metadata["cost_accounting"] == "RESERVED_BOUND"
    provider.analyze.assert_called_once()


def test_missing_prices_are_not_ready_and_do_not_initialize_sdk(
    monkeypatch, catalog_entry, catalog_path, sample_request
):
    setup_model(
        monkeypatch,
        catalog_path([catalog_entry(model="priced-model")]),
        JEV_COST_GUARD_ENABLED="true",
        JEV_REQUEST_BUDGET_USD="1",
        JEV_DAILY_BUDGET_USD="1",
    )
    sdk = Mock(side_effect=AssertionError("No SDK initialization allowed"))
    monkeypatch.setattr("app.providers.router.GeminiProvider", sdk)
    with TestClient(app) as client:
        readiness = client.get("/health/ready")
        assert readiness.status_code == 503
        assert readiness.json()["checks"]["cost_policy"] == "UNAVAILABLE"
        assert post(client, sample_request).status_code == 503
    sdk.assert_not_called()


def test_retry_cannot_bypass_global_attempt_rate(
    monkeypatch, catalog_entry, catalog_path, sample_request
):
    setup_model(
        monkeypatch,
        catalog_path([catalog_entry(model="priced-model")]),
        JEV_ATTEMPTS_PER_MINUTE="1",
        JEV_MAX_ATTEMPTS="2",
        JEV_RETRY_ATTEMPTS="2",
        JEV_RETRY_BASE_DELAY_SECONDS="0",
    )
    provider = FakeProvider()
    provider.model = "priced-model"
    provider.analyze = Mock(side_effect=TransientProviderFailure())
    monkeypatch.setattr("app.providers.router.GeminiProvider", Mock(return_value=provider))
    monkeypatch.setattr("langsmith.Client", Mock(return_value=Mock()))
    with TestClient(app) as client:
        response = post(client, sample_request)
    assert response.status_code == 429
    assert int(response.headers["Retry-After"]) >= 1
    provider.analyze.assert_called_once()


def test_usage_bound_breach_blocks_later_calls_and_readiness(
    monkeypatch, catalog_entry, catalog_path, sample_request
):
    setup_model(
        monkeypatch,
        catalog_path([priced_entry(catalog_entry)]),
        JEV_COST_GUARD_ENABLED="true",
        JEV_REQUEST_BUDGET_USD="0.1",
        JEV_DAILY_BUDGET_USD="1",
    )
    provider = FakeProvider()
    provider.model = "priced-model"
    result = replace(
        FakeProvider().analyze(sample_request),
        input_tokens=21000,
        output_tokens=16000,
        usage_complete=True,
    )
    provider.analyze = Mock(return_value=result)
    monkeypatch.setattr("app.providers.router.GeminiProvider", Mock(return_value=provider))
    monkeypatch.setattr("langsmith.Client", Mock(return_value=Mock()))
    with TestClient(app) as client:
        assert post(client, sample_request).status_code == 503
        assert client.get("/health/ready").status_code == 503
        assert post(client, sample_request).status_code == 503
    provider.analyze.assert_called_once()
