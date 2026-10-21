from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
from unittest.mock import Mock

import pytest
from conftest import FakeProvider, root_run
from fastapi.testclient import TestClient

from app.application.resilient_execution import ResilientExecutor
from app.application.resilient_service import ResilientJevService
from app.chains.release import ACTIVE_RELEASE
from app.core.config import Settings
from app.core.errors import TransientProviderFailure
from app.main import app
from app.providers.routing_policy import route_candidates
from app.schemas.jev import JevRequest


def configure(monkeypatch, path, *, fallback, provider="gemini"):
    for key, value in {
        "APP_ENV": "local",
        "JEV_PROVIDER": provider,
        "OPENAI_API_KEY": "synthetic-openai-key",
        "GOOGLE_API_KEY": "synthetic-key",
        "JEV_SERVICE_TOKEN": "synthetic-token",
        "JEV_ROUTING_POLICY": "priority",
        "JEV_MODEL_PRIORITY": '["first","second"]',
        "JEV_MODEL_CATALOG_PATH": str(path),
        "JEV_MAX_ATTEMPTS": "2" if fallback else "1",
        "JEV_FALLBACK_ENABLED": str(fallback).lower(),
        "JEV_RETRY_BASE_DELAY_SECONDS": "0",
        "JEV_CIRCUIT_FAILURE_THRESHOLD": "1",
        "LANGSMITH_ENABLED": "true",
        "LANGSMITH_API_KEY": "synthetic-trace-key",
    }.items():
        monkeypatch.setenv(key, value)


@pytest.mark.parametrize("provider_name", ["gemini", "openai"])
def test_fallback_response_trace_and_circuit_skip(
    monkeypatch, catalog_entry, catalog_path, sample_request, provider_name
):
    configure(
        monkeypatch,
        catalog_path(
            [catalog_entry(provider_name, "first"), catalog_entry(provider_name, "second")]
        ),
        fallback=True,
        provider=provider_name,
    )
    providers = {}

    def factory(config):
        provider = FakeProvider()
        provider.name = config.jev_provider
        provider.model = config.gemini_model if provider_name == "gemini" else config.openai_model
        if provider.model == "first":
            provider.analyze = Mock(side_effect=TransientProviderFailure())
        providers[provider.model] = provider
        return provider

    sdk = Mock(side_effect=factory)
    client = Mock()
    adapter_name = "GeminiProvider" if provider_name == "gemini" else "OpenAIProvider"
    monkeypatch.setattr(f"app.providers.router.{adapter_name}", sdk)
    monkeypatch.setattr("langsmith.Client", Mock(return_value=client))
    with TestClient(app) as http:
        for expected_attempts, expected_skips in [(2, 0), (1, 1)]:
            response = http.post(
                "/internal/jev",
                json=sample_request.model_dump(mode="json"),
                headers={"Authorization": "Bearer synthetic-token"},
            )
            assert response.status_code == 200
            body = response.json()
            assert body["model"] == "second" and body["provider"] == provider_name
            assert body["submission_status"] == "ON_HOLD"
            assert body["trace_status"] == "RECORDED"
            metadata = root_run(client)["extra"]["metadata"]
            for key, value in ACTIVE_RELEASE.metadata().items():
                assert response.json()[key] == metadata[key] == value
            assert metadata["model"] == "second"
            assert metadata["routing_reason"] == "SAME_PROVIDER_FALLBACK"
            assert metadata["execution_attempts"] == expected_attempts
            assert metadata["execution_circuit_skips"] == expected_skips
            assert metadata["last_workflow_completed_stages"] == "PREPARE,J,E,V"
            assert metadata["last_workflow_failed_stage"] == "NONE"
    assert sdk.call_count == 2
    providers["first"].analyze.assert_called_once()
    assert sum(c.kwargs["name"] == "jev-analysis" for c in client.create_run.call_args_list) == 2
    assert sample_request.documents[0].content_base64 not in str(client.mock_calls)


def test_open_circuit_is_safe_503_without_model_call(
    monkeypatch, catalog_entry, catalog_path, sample_request
):
    configure(monkeypatch, catalog_path([catalog_entry(model="first")]), fallback=False)
    provider = FakeProvider()
    provider.model = "first"
    provider.analyze = Mock(side_effect=TransientProviderFailure())
    monkeypatch.setattr("app.providers.router.GeminiProvider", Mock(return_value=provider))
    trace = Mock()
    monkeypatch.setattr("langsmith.Client", Mock(return_value=trace))
    with TestClient(app) as http:
        first = http.post(
            "/internal/jev",
            json=sample_request.model_dump(mode="json"),
            headers={"Authorization": "Bearer synthetic-token"},
        )
        second = http.post(
            "/internal/jev",
            json=sample_request.model_dump(mode="json"),
            headers={"Authorization": "Bearer synthetic-token"},
        )
    assert first.status_code == 502
    assert second.status_code == 503
    assert second.json()["error"]["code"] == "SERVICE_UNAVAILABLE"
    assert "synthetic" not in second.text
    provider.analyze.assert_called_once()
    roots = [
        c.kwargs for c in trace.create_run.call_args_list if c.kwargs["name"] == "jev-analysis"
    ]
    first_metadata, second_metadata = (root["extra"]["metadata"] for root in roots)
    assert first_metadata["last_workflow_failed_stage"] == "J"
    assert second_metadata["execution_attempts"] == 0
    assert not any(key.startswith("last_workflow_") for key in second_metadata)


def test_concurrent_results_keep_actual_model_request_local(
    catalog_entry, catalog_path, request_data
):
    from uuid import uuid4

    first = JevRequest.model_validate(request_data)
    request_data["request_id"] = str(uuid4())
    second = JevRequest.model_validate(request_data)
    config = Settings(
        app_env="local",
        jev_provider="gemini",
        google_api_key="synthetic-key",
        jev_service_token="synthetic-token",
        jev_routing_policy="priority",
        jev_model_priority=("first", "second"),
        jev_model_catalog_path=catalog_path(
            [catalog_entry(model="first"), catalog_entry(model="second")]
        ),
        jev_max_attempts=2,
        jev_fallback_enabled=True,
        jev_retry_base_delay_seconds=0,
    )
    barrier = Barrier(2)
    primary = FakeProvider()
    primary.model = "first"

    def analyze(request):
        barrier.wait(timeout=5)
        if request.request_id == first.request_id:
            raise TransientProviderFailure()
        return FakeProvider().analyze(request)

    primary.analyze = analyze
    secondary = FakeProvider()
    secondary.model = "second"
    routes = route_candidates(config)
    executor = ResilientExecutor(config, routes, primary, lambda route: secondary)
    trace = Mock()
    trace.record.return_value = (None, "DISABLED")
    runner = ResilientJevService(primary, trace, routes[0], executor)
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(runner.run, (first, second)))
    assert [(r.request_id, r.model) for r in results] == [
        (first.request_id, "second"),
        (second.request_id, "first"),
    ]
