from unittest.mock import Mock

from conftest import FakeProvider
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
    assert result["verification"]["confidence_basis"] == "OFFLINE_STUB"
    assert result["verification"]["factual_consistency"] == "NOT_VERIFIED"


def test_keyless_stub_still_requires_service_authentication(request_data):
    with TestClient(app) as client:
        response = client.post("/internal/jev", json=request_data)
    assert response.status_code == 503


def test_v_rejects_a_stub_making_a_real_recommendation(monkeypatch, sample_request, caplog):
    monkeypatch.setenv("APP_ENV", "local")
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    monkeypatch.setenv("LANGSMITH_ENABLED", "true")
    monkeypatch.setenv("LANGSMITH_API_KEY", "synthetic")
    draft = FakeProvider().analyze(sample_request)
    draft.findings.items[0].item = "SECRET_PATIENT"
    analyze = Mock(return_value=draft)
    trace = Mock()
    monkeypatch.setattr("app.providers.stub_provider.StubProvider.analyze", analyze)
    monkeypatch.setattr("langsmith.Client", Mock(return_value=trace))
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev",
            json=sample_request.model_dump(mode="json"),
            headers={"Authorization": "Bearer test-token"},
        )
    assert response.status_code == 502
    assert response.json()["error"]["code"] == "INVALID_AI_RESPONSE"
    assert "SECRET_PATIENT" not in response.text + caplog.text + str(trace.mock_calls)
    payload = trace.create_run.call_args.kwargs
    assert payload["error"] == "ANALYSIS_FAILED"
    assert not any(key.startswith("verification_") for key in payload["extra"]["metadata"])
    analyze.assert_called_once()
