import os
from functools import lru_cache
from pathlib import Path
from typing import Any, Literal, Self
from urllib.parse import urlsplit

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class ConfigurationError(RuntimeError):
    """Only safe field names, never rejected settings values, may be reported."""


class Settings(BaseSettings):
    # Only the loader opts into local dotenv files. Test/production never read them.
    model_config = SettingsConfigDict(extra="ignore", hide_input_in_errors=True)
    app_env: Literal["local", "test", "staging", "production"] = "local"
    jev_service_token: SecretStr = Field(default=SecretStr(""), repr=False)
    google_api_key: SecretStr = Field(default=SecretStr(""), repr=False)
    openai_api_key: SecretStr = Field(default=SecretStr(""), repr=False)
    openai_model: str = Field(default="", pattern=r"^[A-Za-z0-9._/-]*$", max_length=200)
    gemini_model: str = Field(default="", pattern=r"^[A-Za-z0-9._/-]*$", max_length=200)
    jev_provider: Literal["stub", "gemini", "openai"] = "stub"
    jev_model_catalog_path: Path | None = Field(default=None, repr=False)
    langsmith_enabled: bool = False
    langsmith_api_key: SecretStr = Field(default=SecretStr(""), repr=False)
    langsmith_project: str = Field(default="insai-jev", min_length=1, max_length=200)
    langsmith_endpoint: str = "https://api.smith.langchain.com"

    @model_validator(mode="after")
    def validate_configuration(self) -> Self:
        token = self.jev_service_token.get_secret_value()
        if token and token in {
            self.google_api_key.get_secret_value(),
            self.openai_api_key.get_secret_value(),
            self.langsmith_api_key.get_secret_value(),
        }:
            raise ValueError("Service and provider credentials must be different")
        for key in (self.google_api_key, self.openai_api_key, self.langsmith_api_key):
            if any(char.isspace() for char in key.get_secret_value()):
                raise ValueError("API keys must not contain whitespace")
        if token and (not token.isascii() or any(char.isspace() for char in token)):
            raise ValueError("JEV_SERVICE_TOKEN must be an ASCII token without whitespace")
        if self.app_env in {"staging", "production"}:
            if self.jev_provider != "gemini":
                raise ValueError("Staging/production requires JEV_PROVIDER=gemini")
            if len(token) < 32:
                raise ValueError("JEV_SERVICE_TOKEN must contain at least 32 characters")
        if self.app_env == "test" and (self.jev_provider != "stub" or self.langsmith_enabled):
            raise ValueError("Test environment must use stub with LangSmith disabled")
        if self.jev_provider == "gemini":
            if (
                not token
                or not self.google_api_key.get_secret_value().strip()
                or not self.gemini_model
            ):
                raise ValueError(
                    "Gemini requires JEV_SERVICE_TOKEN, GOOGLE_API_KEY and GEMINI_MODEL"
                )
        if self.langsmith_enabled and not self.langsmith_api_key.get_secret_value().strip():
            raise ValueError("LANGSMITH_ENABLED requires LANGSMITH_API_KEY")
        if self.jev_provider == "openai" and (
            not token or not self.openai_api_key.get_secret_value() or not self.openai_model
        ):
            raise ValueError("OpenAI requires JEV_SERVICE_TOKEN, OPENAI_API_KEY and OPENAI_MODEL")
        endpoint = urlsplit(self.langsmith_endpoint)
        local_http = (
            self.app_env in {"local", "test"}
            and endpoint.scheme == "http"
            and endpoint.hostname in {"localhost", "127.0.0.1", "::1"}
        )
        if (
            not endpoint.hostname
            or (endpoint.scheme != "https" and not local_http)
            or endpoint.username
            or endpoint.password
            or endpoint.query
            or endpoint.fragment
        ):
            raise ValueError("LANGSMITH_ENDPOINT must be a credential-free HTTPS URL")
        return self


@lru_cache
def settings() -> Settings:
    # Settings-source debug mode can print source dictionaries before SecretStr conversion.
    if os.getenv("PYDANTIC_SETTINGS_DEBUG", "").lower() not in {"", "0", "false", "off", "no"}:
        raise ConfigurationError("PYDANTIC_SETTINGS_DEBUG is not allowed")
    environment = os.getenv("APP_ENV", "local")
    secret_path = os.getenv("JEV_SECRETS_DIR")
    try:
        if secret_path and not Path(secret_path).is_dir():
            raise ValueError("Invalid secret directory")
        # Source-control kwargs are supported by BaseSettings at runtime but are
        # absent from Pydantic's generated model signature used by type checkers.
        options: dict[str, Any] = {
            "app_env": environment,
            "_env_file": ".env" if environment == "local" else None,
            "_env_file_encoding": "utf-8",
            "_secrets_dir": secret_path,
        }
        return Settings(**options)
    except Exception:
        # ValidationError may retain complete raw input even with SecretStr fields.
        raise ConfigurationError(
            "Invalid AI service configuration; check APP_ENV, provider, credentials and endpoint"
        ) from None
