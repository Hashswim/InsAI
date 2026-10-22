from dataclasses import FrozenInstanceError
from unittest.mock import Mock

import pytest
from pydantic import ValidationError

from app.core.config import Settings
from app.providers.catalog import CatalogError, ModelProfile, load_catalog
from app.providers.router import build_provider


def test_default_is_offline_only():
    catalog = load_catalog()
    assert catalog.candidates() == ()
    assert catalog.require("stub", "stub-v1").model == "stub-v1"
    with pytest.raises(CatalogError):
        catalog.require("gemini", "unregistered-model")
    with pytest.raises(FrozenInstanceError):
        catalog.profiles = ()


def test_exact_provider_model_lookup_and_optional_metadata(catalog_entry, catalog_path):
    path = catalog_path(
        [
            catalog_entry("gemini", "shared-id", context_window_tokens=100000),
            catalog_entry("openai", "shared-id"),
        ]
    )
    catalog = load_catalog(path)
    assert len(catalog.candidates()) == 2
    assert catalog.require("gemini", "shared-id").context_window_tokens == 100000
    assert catalog.require("openai", "shared-id").context_window_tokens is None
    with pytest.raises(CatalogError):
        catalog.require("gemini", "shared-id-prefix")
    with pytest.raises(ValidationError):
        catalog.profiles[1].enabled = False


@pytest.mark.parametrize(
    "changes",
    [
        {"enabled": False},
        {"capabilities": ["pdf_input"]},
        {"max_pdf_documents": 2},
        {"max_output_tokens": 15999},
    ],
)
def test_incompatible_models_are_not_candidates(catalog_entry, catalog_path, changes):
    catalog = load_catalog(catalog_path([catalog_entry(**changes)]))
    assert catalog.candidates() == ()
    with pytest.raises(CatalogError):
        catalog.require("gemini", "private-test-model")


@pytest.mark.parametrize(
    "changes",
    [
        {"provider": "unsupported"},
        {"model": "*"},
        {"model": ""},
        {"enabled": "true"},
        {"capabilities": ["unknown"]},
        {"max_output_tokens": True},
        {"max_output_tokens": -1},
        {"max_pdf_documents": "3"},
        {"context_window_tokens": 0},
        {"source": "   "},
        {"extra": "SENTINEL"},
    ],
)
def test_invalid_declarations_fail_safely(catalog_entry, catalog_path, changes):
    path = catalog_path([catalog_entry(**changes)])
    with pytest.raises(CatalogError) as error:
        load_catalog(path)
    assert "SENTINEL" not in str(error.value)
    assert str(path) not in str(error.value)


def test_duplicate_and_stub_override_are_rejected(catalog_entry, catalog_path):
    entry = catalog_entry()
    for entries in ([entry, entry], [catalog_entry("stub", "stub-v1")]):
        with pytest.raises(CatalogError):
            load_catalog(catalog_path(entries))


@pytest.mark.parametrize(
    "text",
    [
        "SENTINEL_NOT_JSON",
        '{"schema_version":2,"models":[]}',
        '{"schema_version":true,"models":[]}',
        '{"schema_version":1,"models":[],"models":[]}',
        '{"schema_version":1}',
        "[]",
        "x" * 1_048_577,
    ],
)
def test_invalid_files_do_not_echo_contents(tmp_path, text):
    path = tmp_path / "SENTINEL_PATH.json"
    path.write_text(text, encoding="utf-8")
    with pytest.raises(CatalogError) as error:
        load_catalog(path)
    assert "SENTINEL" not in str(error.value)


def test_missing_file_is_safe(tmp_path):
    with pytest.raises(CatalogError) as error:
        load_catalog(tmp_path / "SENTINEL_MISSING.json")
    assert "SENTINEL" not in str(error.value)


def test_builder_rejects_unknown_before_sdk_initialization(monkeypatch):
    factory = Mock(side_effect=AssertionError("SDK must not initialize"))
    monkeypatch.setattr("app.providers.router.GeminiProvider", factory)
    config = Settings(
        app_env="local",
        jev_provider="gemini",
        jev_service_token="test-token",
        google_api_key="synthetic-key",
        gemini_model="private-test-model",
    )
    with pytest.raises(CatalogError):
        build_provider(config)
    factory.assert_not_called()


def test_builder_uses_registered_model_without_fallback(monkeypatch, catalog_entry, catalog_path):
    factory = Mock()
    monkeypatch.setattr("app.providers.router.GeminiProvider", factory)
    config = Settings(
        app_env="local",
        jev_provider="gemini",
        jev_service_token="test-token",
        google_api_key="synthetic-key",
        gemini_model="private-test-model",
        jev_model_catalog_path=catalog_path([catalog_entry()]),
    )
    assert build_provider(config) is factory.return_value
    factory.assert_called_once_with(config)


def test_registration_cannot_change_environment_policy(catalog_entry, catalog_path):
    with pytest.raises(ValidationError):
        Settings(
            app_env="production",
            jev_provider="openai",
            jev_service_token="x" * 32,
            openai_api_key="synthetic",
            openai_model="private-test-model",
            jev_model_catalog_path=catalog_path([catalog_entry("openai")]),
        )


def test_profile_does_not_guess_capabilities(catalog_entry):
    entry = catalog_entry()
    entry.pop("capabilities")
    with pytest.raises(ValidationError):
        ModelProfile.model_validate(entry)
