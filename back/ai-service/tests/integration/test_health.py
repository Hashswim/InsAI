from importlib.metadata import version
from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.version import package_version


def test_live_without_credentials():
    with TestClient(app) as client:
        response = client.get("/health/live")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}
    assert response.headers["cache-control"] == "no-store"


def test_missing_service_token_is_not_ready():
    with TestClient(app) as client:
        response = client.get("/health/ready")
    assert response.status_code == 503
    assert response.json()["checks"]["service_auth"] == "MISSING"
    assert response.headers["cache-control"] == "no-store"


def test_stub_is_ready_without_external_connections(monkeypatch):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    google = Mock(side_effect=AssertionError("No model initialization allowed"))
    smith = Mock(side_effect=AssertionError("No trace initialization allowed"))
    monkeypatch.setattr("app.providers.gemini_provider.ChatGoogleGenerativeAI", google)
    monkeypatch.setattr("langsmith.Client", smith)
    with TestClient(app) as client:
        response = client.get("/health/ready")
    assert response.status_code == 200
    assert response.json()["mode"] == "stub"
    assert response.json()["external_connections"] == "NOT_PROBED"
    google.assert_not_called()
    smith.assert_not_called()


def test_gemini_readiness_does_not_claim_connection_or_expose_config(
    monkeypatch, catalog_entry, catalog_path
):
    path = catalog_path([catalog_entry()])
    monkeypatch.setenv("JEV_MODEL_CATALOG_PATH", str(path))
    monkeypatch.setenv("APP_ENV", "local")
    monkeypatch.setenv("JEV_PROVIDER", "gemini")
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "SENTINEL_TOKEN")
    monkeypatch.setenv("GOOGLE_API_KEY", "SENTINEL_GOOGLE")
    monkeypatch.setenv("GEMINI_MODEL", "private-test-model")
    monkeypatch.setenv("LANGSMITH_ENABLED", "true")
    monkeypatch.setenv("LANGSMITH_API_KEY", "SENTINEL_SMITH")
    monkeypatch.setenv("LANGSMITH_PROJECT", "private-project")
    monkeypatch.setattr(
        "app.providers.gemini_provider.ChatGoogleGenerativeAI",
        Mock(side_effect=AssertionError("No provider probe allowed")),
    )
    monkeypatch.setattr("langsmith.Client", Mock(side_effect=AssertionError("Optional only")))
    with TestClient(app) as client:
        response = client.get("/health/ready")
    assert response.status_code == 200
    assert response.json()["mode"] == "gemini"
    assert response.json()["checks"]["langsmith"] == "CONFIGURED"
    assert response.json()["external_connections"] == "NOT_PROBED"
    assert "SENTINEL" not in response.text
    assert "private-" not in response.text


def test_missing_packaged_prompt_is_not_ready_but_alive(monkeypatch):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    monkeypatch.setattr(
        "app.api.health.load_jev_prompt", Mock(side_effect=OSError("SENTINEL_INTERNAL_PATH"))
    )
    with TestClient(app) as client:
        response = client.get("/health/ready")
        assert client.get("/health/live").status_code == 200
    assert response.status_code == 503
    assert response.json()["checks"]["prompt"] == "UNAVAILABLE"
    assert "SENTINEL" not in response.text


def test_shutdown_is_not_ready(monkeypatch):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    with TestClient(app) as client:
        assert client.get("/health/ready").status_code == 200
    assert app.state.ready is False
    # A request without a new lifespan must still see shutdown, not stale READY.
    response = TestClient(app).get("/health/ready")
    assert response.status_code == 503
    assert response.json()["checks"]["startup"] == "INCOMPLETE"


def test_version_matches_installed_metadata_and_openapi():
    with TestClient(app) as client:
        response = client.get("/version")
        schema = client.get("/openapi.json").json()
    assert response.status_code == 200
    assert response.json() == {
        "service": "insai-ai-service",
        "version": version("insai-ai-service"),
    }
    assert response.json()["version"] == schema["info"]["version"]


def test_uninstalled_source_version_is_explicit_unknown(monkeypatch):
    from importlib.metadata import PackageNotFoundError

    monkeypatch.setattr("app.version.version", Mock(side_effect=PackageNotFoundError()))
    assert package_version() == "0+unknown"


@pytest.mark.parametrize("declaration", ["missing", "disabled", "incompatible", "invalid"])
def test_catalog_blocks_readiness_and_analysis_without_sdk(
    monkeypatch, catalog_entry, catalog_path, sample_request, declaration
):
    monkeypatch.setenv("APP_ENV", "local")
    monkeypatch.setenv("JEV_PROVIDER", "gemini")
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    monkeypatch.setenv("GOOGLE_API_KEY", "synthetic-key")
    monkeypatch.setenv("GEMINI_MODEL", "private-test-model")
    if declaration != "missing":
        changes = {
            "disabled": {"enabled": False},
            "incompatible": {"max_pdf_documents": 1},
            "invalid": {"provider": "SENTINEL_UNSUPPORTED"},
        }[declaration]
        path = catalog_path([catalog_entry(**changes)])
        monkeypatch.setenv("JEV_MODEL_CATALOG_PATH", str(path))
    factory = Mock(side_effect=AssertionError("No SDK initialization allowed"))
    monkeypatch.setattr("app.providers.router.GeminiProvider", factory)
    with TestClient(app) as client:
        readiness = client.get("/health/ready")
        assert client.get("/health/live").status_code == 200
        result = client.post(
            "/internal/jev",
            headers={"Authorization": "Bearer test-token"},
            json=sample_request.model_dump(mode="json"),
        )
    assert readiness.status_code == 503
    assert readiness.json()["checks"]["model_catalog"] == "UNAVAILABLE"
    assert result.status_code == 503
    assert result.json()["error"]["code"] == "SERVICE_UNAVAILABLE"
    assert "SENTINEL" not in readiness.text + result.text
    assert "private-" not in readiness.text + result.text
    factory.assert_not_called()
