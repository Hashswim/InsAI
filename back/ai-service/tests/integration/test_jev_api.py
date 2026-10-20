from fastapi.testclient import TestClient
from app.main import app
from app.api.jev import service
from app.application.jev_service import JevService
from app.core.config import settings
from unittest.mock import Mock
from conftest import FakeProvider


def client(monkeypatch):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    settings.cache_clear()
    trace = Mock()
    trace.record.return_value = (None, "DISABLED")
    app.dependency_overrides[service] = lambda: JevService(FakeProvider(), trace)
    return TestClient(app)


def test_end_to_end_api(monkeypatch, request_data):
    with client(monkeypatch) as http:
        response = http.post("/internal/jev", json=request_data,
                             headers={"Authorization": "Bearer test-token"})
    app.dependency_overrides.clear()
    assert response.status_code == 200
    assert response.json()["submission_status"] == "ON_HOLD"
    assert response.json()["model"] == "fake-test-model"


def test_unauthenticated(monkeypatch, request_data):
    with client(monkeypatch) as http:
        assert http.post("/internal/jev", json=request_data).status_code == 401
    app.dependency_overrides.clear()


def test_validation_error_does_not_echo_pdf(monkeypatch, request_data):
    request_data["documents"][0]["content_base64"] = "SECRET_PATIENT"
    with client(monkeypatch) as http:
        response = http.post("/internal/jev", json=request_data,
                             headers={"Authorization": "Bearer test-token"})
    app.dependency_overrides.clear()
    assert response.status_code == 422
    assert "SECRET_PATIENT" not in response.text
