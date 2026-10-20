from fastapi.testclient import TestClient

from app.main import app


def test_keyless_stub_runs_real_route_without_external_calls(monkeypatch, request_data):
    def forbidden(*args, **kwargs):
        raise AssertionError("External SDK must not be initialized in stub mode")

    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    monkeypatch.setattr("app.providers.gemini_provider.ChatGoogleGenerativeAI", forbidden)
    monkeypatch.setattr("langsmith.Client", forbidden)
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev", json=request_data, headers={"Authorization": "Bearer test-token"}
        )
    assert response.status_code == 200
    result = response.json()
    assert result["provider"] == "stub"
    assert result["model"] == "stub-v1"
    assert result["submission_status"] == "ON_HOLD"
    assert len(result["items"]) == 3
    assert all(
        item["judgment"] == "UNVERIFIABLE" and item["confidence"] == 0 for item in result["items"]
    )
    assert result["trace_status"] == "DISABLED"


def test_keyless_stub_still_requires_service_authentication(request_data):
    with TestClient(app) as client:
        response = client.post("/internal/jev", json=request_data)
    assert response.status_code == 503
