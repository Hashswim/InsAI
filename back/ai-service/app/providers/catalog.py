"""Local, declarative model registry. Never discovers models or calls a provider."""

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, StrictBool, model_validator

ProviderName = Literal["stub", "gemini", "openai"]
Capability = Literal["pdf_input", "structured_output", "token_usage"]
JEV_CAPABILITIES: frozenset[Capability] = frozenset({"pdf_input", "structured_output"})
JEV_OUTPUT_TOKENS = 16000


class CatalogError(RuntimeError):
    """Safe errors: never include file contents, paths or rejected values."""


class ModelProfile(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, hide_input_in_errors=True)
    provider: ProviderName
    model: str = Field(min_length=1, max_length=200, pattern=r"^[A-Za-z0-9._/-]+$")
    enabled: StrictBool = True
    capabilities: frozenset[Capability]
    max_pdf_documents: int = Field(strict=True, ge=1, le=1000)
    max_output_tokens: int = Field(strict=True, ge=1)
    context_window_tokens: int | None = Field(default=None, strict=True, ge=1)
    source: str = Field(min_length=1, max_length=1000, repr=False)

    @model_validator(mode="after")
    def nonblank_source(self) -> "ModelProfile":
        if not self.source.strip():
            raise ValueError("A capability declaration source is required")
        return self


class CatalogFile(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True, hide_input_in_errors=True)
    schema_version: int = Field(strict=True, ge=1, le=1)
    models: tuple[ModelProfile, ...] = Field(max_length=100)


STUB_PROFILE = ModelProfile(
    provider="stub",
    model="stub-v1",
    capabilities=frozenset({"pdf_input", "structured_output"}),
    max_pdf_documents=3,
    max_output_tokens=JEV_OUTPUT_TOKENS,
    source="builtin:offline-connectivity-only; no model interpretation",
)


@dataclass(frozen=True)
class ModelCatalog:
    profiles: tuple[ModelProfile, ...]

    def __post_init__(self) -> None:
        keys = [(p.provider, p.model) for p in self.profiles]
        if len(keys) != len(set(keys)):
            raise CatalogError("Duplicate model registration")

    def candidates(
        self,
        required: frozenset[Capability] = JEV_CAPABILITIES,
        *,
        allow_offline: bool = False,
    ) -> tuple[ModelProfile, ...]:
        return tuple(
            p
            for p in self.profiles
            if p.enabled
            and required <= p.capabilities
            and p.max_pdf_documents >= 3
            and p.max_output_tokens >= JEV_OUTPUT_TOKENS
            and (allow_offline or p.provider != "stub")
        )

    def require(self, provider: ProviderName, model: str) -> ModelProfile:
        for profile in self.candidates(allow_offline=provider == "stub"):
            if (profile.provider, profile.model) == (provider, model):
                return profile
        raise CatalogError("Selected model is unavailable or incompatible")


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key")
        result[key] = value
    return result


def load_catalog(path: Path | None = None) -> ModelCatalog:
    if path is None:
        return ModelCatalog((STUB_PROFILE,))
    try:
        if not path.is_file():
            raise ValueError("Catalog must be a regular file")
        with path.open("rb") as stream:
            raw = stream.read(1_048_577)
        if len(raw) > 1_048_576:
            raise ValueError("Catalog too large")
        data = CatalogFile.model_validate(json.loads(raw, object_pairs_hook=_unique_object))
        if any(p.provider == "stub" for p in data.models):
            raise ValueError("Built-in stub cannot be overridden")
        return ModelCatalog((STUB_PROFILE, *data.models))
    except Exception:
        raise CatalogError("Invalid model catalog configuration") from None


def selected_profile(catalog: ModelCatalog, provider: ProviderName, model: str) -> ModelProfile:
    return catalog.require(provider, "stub-v1" if provider == "stub" else model)
