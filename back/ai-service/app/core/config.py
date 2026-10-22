import os
from decimal import Decimal
from functools import lru_cache
from pathlib import Path
from typing import Annotated, Any, Literal, Self
from urllib.parse import urlsplit

from pydantic import Field, SecretStr, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

from app.providers.routing_policy import RoutingPolicy

ModelId = Annotated[
    str, Field(strict=True, min_length=1, max_length=200, pattern=r"^[A-Za-z0-9._/-]+$")
]


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
    openai_reasoning_effort: Literal["none", "low", "medium", "high", "xhigh", "max"] | None = None
    openai_verbosity: Literal["low", "medium", "high"] | None = None
    gemini_model: str = Field(default="", pattern=r"^[A-Za-z0-9._/-]*$", max_length=200)
    jev_provider: Literal["stub", "gemini", "openai"] = "stub"
    jev_model_catalog_path: Path | None = Field(default=None, repr=False)
    jev_routing_policy: RoutingPolicy = "pinned"
    jev_model_priority: tuple[ModelId, ...] = Field(default=(), max_length=20, repr=False)
    jev_max_attempts: int = Field(default=1, ge=1, le=5)
    jev_retry_attempts: int = Field(default=1, ge=1, le=3)
    jev_fallback_enabled: bool = False
    jev_attempt_timeout_seconds: int = Field(default=90, ge=1, le=100)
    jev_execution_budget_seconds: int = Field(default=110, ge=1, le=115)
    jev_retry_base_delay_seconds: float = Field(default=0.25, ge=0, le=2)
    jev_circuit_enabled: bool = True
    jev_circuit_failure_threshold: int = Field(default=5, ge=1, le=20)
    jev_circuit_cooldown_seconds: int = Field(default=30, ge=1, le=300)
    jev_limits_enabled: bool = True
    jev_requests_per_minute: int = Field(default=30, ge=1, le=100000)
    jev_attempts_per_minute: int = Field(default=60, ge=1, le=100000)
    jev_max_concurrent_requests: int = Field(default=2, ge=1, le=1000)
    jev_max_concurrent_calls: int = Field(default=2, ge=1, le=1000)
    jev_cost_guard_enabled: bool = False
    jev_request_budget_usd: Decimal | None = Field(
        default=None, gt=0, le=1000000, allow_inf_nan=False
    )
    jev_daily_budget_usd: Decimal | None = Field(
        default=None, gt=0, le=1000000, allow_inf_nan=False
    )
    jev_pricing_max_age_days: int = Field(default=30, ge=1, le=365)
    langsmith_enabled: bool = False
    langsmith_api_key: SecretStr = Field(default=SecretStr(""), repr=False)
    langsmith_project: str = Field(default="insai-jev", min_length=1, max_length=200)
    langsmith_endpoint: str = "https://api.smith.langchain.com"
    # LangGraph supervisor + specialist agents for complete bundles (J stage); off = single call.
    jev_multi_agent: bool = False
    # Standard-terms RAG (pgvector). Empty URL disables retrieval; agents then cite no clauses.
    jev_rag_database_url: SecretStr = Field(default=SecretStr(""), repr=False)
    jev_embedding_model: str = Field(
        default="text-embedding-3-small", pattern=r"^[A-Za-z0-9._/-]+$", max_length=200
    )
    jev_rag_top_k: int = Field(default=6, ge=1, le=12)

    @model_validator(mode="after")
    def validate_configuration(self) -> Self:
        if self.jev_cost_guard_enabled:
            if (
                self.jev_provider == "stub"
                or self.jev_request_budget_usd is None
                or self.jev_daily_budget_usd is None
            ):
                raise ValueError(
                    "Cost guard requires a real provider and explicit request/daily USD budgets"
                )
        elif self.jev_request_budget_usd is not None or self.jev_daily_budget_usd is not None:
            raise ValueError("USD budgets require the cost guard to be enabled")
        if self.jev_retry_attempts > self.jev_max_attempts:
            raise ValueError("Per-model attempts cannot exceed total attempts")
        if self.jev_execution_budget_seconds < self.jev_attempt_timeout_seconds:
            raise ValueError("Execution budget must cover one attempt timeout")
        if self.jev_fallback_enabled and (
            self.jev_routing_policy != "priority" or self.jev_max_attempts < 2
        ):
            raise ValueError("Fallback requires priority routing and multiple total attempts")
        if self.jev_provider == "stub" and (
            self.jev_max_attempts != 1 or self.jev_retry_attempts != 1 or self.jev_fallback_enabled
        ):
            raise ValueError("Stub execution must remain single-attempt")
        if self.jev_routing_policy == "priority":
            if self.jev_provider == "stub" or not self.jev_model_priority:
                raise ValueError(
                    "Priority routing requires a real provider and explicit model order"
                )
            if len(set(self.jev_model_priority)) != len(self.jev_model_priority):
                raise ValueError("Priority routing model IDs must be unique")
        elif self.jev_model_priority:
            raise ValueError("Model priority is only valid with priority routing")
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
                or (self.jev_routing_policy == "pinned" and not self.gemini_model)
            ):
                raise ValueError(
                    "Gemini requires service credentials and a routing model configuration"
                )
        if self.jev_multi_agent and self.jev_provider == "stub":
            raise ValueError("Multi-agent analysis requires a real provider")
        rag_url = self.jev_rag_database_url.get_secret_value()
        if rag_url and (
            urlsplit(rag_url).scheme not in {"postgresql", "postgres"}
            or not self.openai_api_key.get_secret_value()
        ):
            raise ValueError("RAG requires a PostgreSQL URL and an OpenAI key for embeddings")
        if self.langsmith_enabled and not self.langsmith_api_key.get_secret_value().strip():
            raise ValueError("LANGSMITH_ENABLED requires LANGSMITH_API_KEY")
        if self.jev_provider == "openai" and (
            not token
            or not self.openai_api_key.get_secret_value()
            or (self.jev_routing_policy == "pinned" and not self.openai_model)
        ):
            raise ValueError(
                "OpenAI requires service credentials and a routing model configuration"
            )
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
