from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient

from app.chains.release import ACTIVE_RELEASE
from app.main import app


def test_stub_result_and_safe_trace_share_release_identity(monkeypatch, sample_request):
    monkeypatch.setenv("APP_ENV", "local")
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "synthetic-token")
    monkeypatch.setenv("LANGSMITH_ENABLED", "true")
    monkeypatch.setenv("LANGSMITH_API_KEY", "synthetic-trace-key")
    trace_client = Mock()
    monkeypatch.setattr("langsmith.Client", Mock(return_value=trace_client))
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev",
            json=sample_request.model_dump(mode="json"),
            headers={"Authorization": "Bearer synthetic-token", "X-Prompt-Version": "jev-v999"},
        )
    assert response.status_code == 200
    payload = trace_client.create_run.call_args.kwargs
    for key, value in ACTIVE_RELEASE.metadata().items():
        assert response.json()[key] == payload["extra"]["metadata"][key] == value
    assert payload["inputs"] == payload["outputs"] == {}
    assert "Judgment" not in str(payload)
    assert sample_request.documents[0].content_base64 not in str(payload)


@pytest.mark.parametrize(
    "field", ["prompt_version", "prompt_sha256", "chain_version", "prompt", "verification"]
)
def test_request_cannot_override_release(monkeypatch, sample_request, field):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "synthetic-token")
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev",
            json={**sample_request.model_dump(mode="json"), field: "untrusted"},
            headers={"Authorization": "Bearer synthetic-token"},
        )
    assert response.status_code == 422


def test_corrupt_prompt_blocks_readiness_and_analysis_before_adapter_init(
    monkeypatch, sample_request
):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "synthetic-token")
    resources = Mock()
    resources.return_value.joinpath.return_value.read_bytes.return_value = b"SECRET_CORRUPT_PROMPT"
    monkeypatch.setattr("app.core.resources.files", resources)
    adapter = Mock()
    monkeypatch.setattr("app.api.jev.build_adapter", adapter)
    with TestClient(app) as client:
        assert client.get("/health/live").status_code == 200
        ready = client.get("/health/ready")
        response = client.post(
            "/internal/jev",
            json=sample_request.model_dump(mode="json"),
            headers={"Authorization": "Bearer synthetic-token"},
        )
    assert ready.status_code == response.status_code == 503
    assert ready.json()["checks"]["prompt"] == "UNAVAILABLE"
    assert "SECRET" not in ready.text + response.text
    adapter.assert_not_called()
