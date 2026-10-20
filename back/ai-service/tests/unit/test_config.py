import logging
from urllib.parse import quote

import pytest
from fastapi.testclient import TestClient
from pydantic import ValidationError

from app.core.config import ConfigurationError, Settings, settings
from app.core.logging import install_secret_redaction
from app.main import app


def test_local_dotenv_is_loaded_and_environment_wins(monkeypatch, tmp_path):
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("APP_ENV", "local")
    (tmp_path / ".env").write_text("JEV_SERVICE_TOKEN=file-token\nAPP_ENV=production\n")
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "environment-token")
    config = settings()
    assert config.app_env == "local"
    assert config.jev_service_token.get_secret_value() == "environment-token"
    settings.cache_clear()
    monkeypatch.delenv("JEV_SERVICE_TOKEN")
    assert settings().jev_service_token.get_secret_value() == "file-token"


@pytest.mark.parametrize("environment", ["test", "production", "staging"])
def test_nonlocal_never_loads_dotenv(monkeypatch, tmp_path, environment):
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("APP_ENV", environment)
    (tmp_path / ".env").write_text("JEV_SERVICE_TOKEN=must-not-load\n")
    if environment == "test":
        assert settings().jev_service_token.get_secret_value() == ""
    else:
        with pytest.raises(ConfigurationError):
            settings()


def test_mounted_secrets_and_environment_priority(monkeypatch, tmp_path):
    (tmp_path / "JEV_SERVICE_TOKEN").write_text("mounted-token")
    monkeypatch.setenv("JEV_SECRETS_DIR", str(tmp_path))
    assert settings().jev_service_token.get_secret_value() == "mounted-token"
    settings.cache_clear()
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "env-token")
    assert settings().jev_service_token.get_secret_value() == "env-token"


@pytest.mark.parametrize(
    "variable,value",
    [
        ("APP_ENV", "invalid-SECRET"),
        ("JEV_PROVIDER", "invalid-SECRET"),
        ("JEV_SECRETS_DIR", "/nonexistent-SECRET"),
        ("LANGSMITH_ENDPOINT", "https://user:SECRET@example.com"),
        ("PYDANTIC_SETTINGS_DEBUG", "true"),
    ],
)
def test_configuration_error_does_not_echo_values(monkeypatch, variable, value):
    monkeypatch.setenv(variable, value)
    with pytest.raises(ConfigurationError) as error:
        settings()
    assert "SECRET" not in str(error.value)


def test_production_requires_complete_real_configuration(monkeypatch):
    monkeypatch.setenv("APP_ENV", "production")
    monkeypatch.setenv("JEV_PROVIDER", "gemini")
    with pytest.raises(ConfigurationError):
        settings()
    settings.cache_clear()
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "x" * 32)
    monkeypatch.setenv("GOOGLE_API_KEY", "synthetic-google-key")
    monkeypatch.setenv("GEMINI_MODEL", "test-model")
    assert settings().app_env == "production"


@pytest.mark.parametrize("missing", ["JEV_SERVICE_TOKEN", "GOOGLE_API_KEY", "GEMINI_MODEL"])
def test_each_gemini_credential_is_required(monkeypatch, missing):
    monkeypatch.setenv("APP_ENV", "local")
    monkeypatch.setenv("JEV_PROVIDER", "gemini")
    for name, value in {
        "JEV_SERVICE_TOKEN": "test-token",
        "GOOGLE_API_KEY": "fake-key",
        "GEMINI_MODEL": "test-model",
    }.items():
        monkeypatch.setenv(name, value)
    monkeypatch.delenv(missing)
    with pytest.raises(ConfigurationError):
        settings()


def test_production_rejects_short_token_and_http_endpoint():
    fields = {
        "app_env": "production",
        "jev_provider": "gemini",
        "google_api_key": "fake-key",
        "gemini_model": "test-model",
    }
    with pytest.raises(ValidationError):
        Settings(**fields, jev_service_token="short")
    with pytest.raises(ValidationError):
        Settings(**fields, jev_service_token="x" * 32, langsmith_endpoint="http://localhost:8080")


@pytest.mark.parametrize(
    "changes",
    [
        {"app_env": "production", "jev_provider": "stub"},
        {"app_env": "local", "jev_provider": "gemini"},
        {"app_env": "local", "langsmith_enabled": True},
        {"langsmith_endpoint": "http://remote.example"},
        {"jev_service_token": "invalid token"},
        {"google_api_key": "invalid\nkey"},
        {"langsmith_api_key": "invalid key"},
        {"app_env": "test", "langsmith_enabled": True},
    ],
)
def test_incomplete_or_unsafe_settings_are_rejected(changes):
    with pytest.raises(ValidationError):
        Settings(**changes)


def test_settings_repr_and_serialization_hide_keys():
    config = Settings(
        google_api_key="SENTINEL_GOOGLE",
        langsmith_api_key="SENTINEL_SMITH",
        jev_service_token="SENTINEL_TOKEN",
    )
    assert "SENTINEL" not in repr(config)
    assert "SENTINEL" not in config.model_dump_json()


def test_log_redaction_covers_args_url_encoding_and_tracebacks(caplog):
    secret = "sentinel/key+with=chars"
    restore = install_secret_redaction(Settings(google_api_key=secret))
    try:
        with caplog.at_level(logging.WARNING):
            logger = logging.getLogger("insai.config.test")
            logger.warning("API key=%s URL=%s", secret, quote(secret, safe=""))
            try:
                raise RuntimeError(secret)
            except RuntimeError:
                logger.exception("provider failure")
        assert secret not in caplog.text
        assert quote(secret, safe="") not in caplog.text
        assert "[REDACTED]" in caplog.text
    finally:
        restore()


def test_invalid_settings_fail_at_startup(monkeypatch):
    monkeypatch.setenv("APP_ENV", "production")
    with pytest.raises(ConfigurationError):
        with TestClient(app):
            pass
