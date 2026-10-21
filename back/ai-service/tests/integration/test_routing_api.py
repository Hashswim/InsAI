from unittest.mock import Mock

from conftest import FakeProvider, root_run
from fastapi.testclient import TestClient

from app.main import app


def test_priority_route_matches_readiness_response_and_trace(
    monkeypatch, sample_request, catalog_entry, catalog_path
):
    path = catalog_path(
        [catalog_entry(model="first", enabled=False), catalog_entry(model="second")]
    )
    for key, value in {
        "APP_ENV": "local",
        "JEV_PROVIDER": "gemini",
        "GOOGLE_API_KEY": "synthetic-key",
        "JEV_SERVICE_TOKEN": "synthetic-token",
        "JEV_ROUTING_POLICY": "priority",
        "JEV_MODEL_PRIORITY": '["first","second"]',
        "JEV_MODEL_CATALOG_PATH": str(path),
        "LANGSMITH_ENABLED": "true",
        "LANGSMITH_API_KEY": "synthetic-trace-key",
    }.items():
        monkeypatch.setenv(key, value)

    def factory(config):
        provider = FakeProvider()
        provider.model = config.gemini_model
        return provider

    sdk = Mock(side_effect=factory)
    trace_client = Mock()
    monkeypatch.setattr("app.providers.router.GeminiProvider", sdk)
    monkeypatch.setattr("langsmith.Client", Mock(return_value=trace_client))
    with TestClient(app) as client:
        ready = client.get("/health/ready")
        assert ready.status_code == 200
        assert ready.json()["checks"]["routing"] == "SELECTABLE"
        sdk.assert_not_called()
        response = client.post(
            "/internal/jev",
            json=sample_request.model_dump(mode="json"),
            headers={
                "Authorization": "Bearer synthetic-token",
                "X-Model": "first",
                "X-Provider": "openai",
            },
        )
    assert response.status_code == 200
    assert response.json()["provider"] == "gemini"
    assert response.json()["model"] == "second"
    assert response.json()["submission_status"] == "ON_HOLD"
    assert response.json()["trace_status"] == "RECORDED"
    metadata = root_run(trace_client)["extra"]["metadata"]
    assert metadata["model"] == "second"
    assert metadata["routing_priority_index"] == 1
    assert "content_base64" not in str(trace_client.mock_calls)
    sdk.assert_called_once()


def test_client_cannot_submit_routing_policy(monkeypatch, sample_request):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "synthetic-token")
    with TestClient(app) as client:
        body = sample_request.model_dump(mode="json")
        body["routing_policy"] = "priority"
        response = client.post(
            "/internal/jev", json=body, headers={"Authorization": "Bearer synthetic-token"}
        )
    assert response.status_code == 422
