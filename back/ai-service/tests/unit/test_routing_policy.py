from datetime import datetime, timezone
from unittest.mock import Mock

import pytest
from conftest import FakeProvider
from pydantic import ValidationError

from app.application.jev_service import JevService
from app.core.config import Settings
from app.core.errors import ProviderFailure
from app.observability.langsmith import SafeTrace
from app.providers.catalog import CatalogError
from app.providers.router import build_routed_provider
from app.providers.routing_policy import RouteDecision, RouteUnavailable, select_route


def configured(path, **changes):
    return Settings(
        **{
            "app_env": "local",
            "jev_provider": "gemini",
            "jev_service_token": "synthetic-token",
            "google_api_key": "synthetic-key",
            "gemini_model": "chosen-model",
            "jev_model_catalog_path": path,
            **changes,
        }
    )


def test_default_pinned_does_not_choose_another_model(catalog_entry, catalog_path):
    config = configured(catalog_path([catalog_entry(model="other-model")]))
    with pytest.raises(CatalogError):
        select_route(config)
    config = configured(catalog_path([catalog_entry(model="chosen-model")]))
    decision = select_route(config)
    assert decision.reason == "PINNED_MODEL"
    assert decision.priority_index is None
    assert decision.profile.model == "chosen-model"


def test_priority_is_explicit_order_not_catalog_order(catalog_entry, catalog_path):
    path = catalog_path([catalog_entry(model="second"), catalog_entry(model="first")])
    decision = select_route(
        configured(
            path,
            jev_routing_policy="priority",
            jev_model_priority=("first", "second"),
            gemini_model="",
        )
    )
    assert decision.profile.model == "first"
    assert decision.reason == "FIRST_COMPATIBLE_PRIORITY"
    assert decision.priority_index == 0


def test_preflight_skips_unknown_disabled_and_incompatible(catalog_entry, catalog_path):
    path = catalog_path(
        [
            catalog_entry(model="disabled", enabled=False),
            catalog_entry(model="no-schema", capabilities=["pdf_input"]),
            catalog_entry(model="chosen-model"),
        ]
    )
    decision = select_route(
        configured(
            path,
            jev_routing_policy="priority",
            jev_model_priority=("unknown", "disabled", "no-schema", "chosen-model"),
        )
    )
    assert decision.profile.model == "chosen-model"
    assert decision.priority_index == 3


def test_priority_never_crosses_provider_or_uses_unlisted_model(catalog_entry, catalog_path):
    path = catalog_path([catalog_entry("openai", "first"), catalog_entry(model="unlisted")])
    with pytest.raises(RouteUnavailable) as error:
        select_route(configured(path, jev_routing_policy="priority", jev_model_priority=("first",)))
    assert "first" not in str(error.value)


@pytest.mark.parametrize(
    "changes",
    [
        {"jev_routing_policy": "unknown"},
        {"jev_routing_policy": "priority", "jev_model_priority": ()},
        {"jev_routing_policy": "priority", "jev_model_priority": ("same", "same")},
        {"jev_routing_policy": "priority", "jev_model_priority": ("*",)},
        {"jev_routing_policy": "pinned", "jev_model_priority": ("unused",)},
        {"jev_routing_policy": "priority", "jev_model_priority": ("one",), "jev_provider": "stub"},
        {
            "app_env": "production",
            "jev_provider": "openai",
            "jev_service_token": "x" * 32,
            "openai_api_key": "synthetic",
            "jev_routing_policy": "priority",
            "jev_model_priority": ("one",),
        },
    ],
)
def test_invalid_or_unauthorized_policy_is_rejected(changes):
    with pytest.raises(ValidationError):
        configured(None, **changes)


def test_offline_stub_is_explicit_not_a_fallback():
    decision = select_route(Settings())
    assert decision.profile.provider == "stub"
    assert decision.reason == "OFFLINE_STUB"


def test_only_one_selected_sdk_is_initialized(monkeypatch, catalog_entry, catalog_path):
    factory = Mock()
    other = Mock(side_effect=AssertionError("Other provider must never initialize"))
    monkeypatch.setattr("app.providers.router.GeminiProvider", factory)
    monkeypatch.setattr("app.providers.router.OpenAIProvider", other)
    path = catalog_path([catalog_entry(model="first"), catalog_entry(model="second")])
    config = configured(path, jev_routing_policy="priority", jev_model_priority=("first", "second"))
    _, route = build_routed_provider(config)
    selected = factory.call_args.args[0]
    assert selected.gemini_model == route.profile.model == "first"
    assert selected.google_api_key == config.google_api_key
    assert config.gemini_model == "chosen-model"
    factory.assert_called_once()
    other.assert_not_called()


def test_sdk_initialization_failure_does_not_try_next(monkeypatch, catalog_entry, catalog_path):
    factory = Mock(side_effect=RuntimeError("SDK failure"))
    monkeypatch.setattr("app.providers.router.GeminiProvider", factory)
    path = catalog_path([catalog_entry(model="first"), catalog_entry(model="second")])
    config = configured(path, jev_routing_policy="priority", jev_model_priority=("first", "second"))
    with pytest.raises(RuntimeError):
        build_routed_provider(config)
    factory.assert_called_once()


def test_call_failure_does_not_reroute_or_retry(sample_request, catalog_entry):
    from app.providers.catalog import ModelProfile

    provider = FakeProvider()
    provider.analyze = Mock(side_effect=RuntimeError("SECRET_PATIENT"))
    route = RouteDecision(
        ModelProfile.model_validate(catalog_entry(model=provider.model)), "priority", 1
    )
    trace = Mock()
    with pytest.raises(ProviderFailure):
        JevService(provider, trace, route).run(sample_request)
    provider.analyze.assert_called_once()
    assert trace.record.call_args.kwargs["route"] == route


def test_route_provider_mismatch_is_rejected(catalog_entry):
    from app.providers.catalog import ModelProfile

    route = RouteDecision(ModelProfile.model_validate(catalog_entry()), "pinned")
    with pytest.raises(ValueError, match="does not match"):
        JevService(FakeProvider(), Mock(), route)


def test_trace_exports_only_route_constants_and_index(monkeypatch, catalog_entry):
    from app.providers.catalog import ModelProfile

    client = Mock()
    monkeypatch.setattr("langsmith.Client", Mock(return_value=client))
    config = Settings(app_env="local", langsmith_enabled=True, langsmith_api_key="synthetic-key")
    route = RouteDecision(
        ModelProfile.model_validate(catalog_entry(source="SECRET_SOURCE")), "priority", 2
    )
    SafeTrace(config).record(datetime.now(timezone.utc), "gemini", "test-model", False, route=route)
    payload = client.create_run.call_args.kwargs
    metadata = payload["extra"]["metadata"]
    assert metadata["routing_reason"] == "FIRST_COMPATIBLE_PRIORITY"
    assert metadata["router_version"] == "jev-router-v1"
    assert metadata["routing_priority_index"] == 2
    assert payload["inputs"] == payload["outputs"] == {}
    assert "SECRET_SOURCE" not in str(payload)
    assert "private-test-model" not in str(payload)


def test_priority_settings_can_be_loaded_from_environment(monkeypatch):
    from app.core.config import settings

    monkeypatch.setenv("APP_ENV", "local")
    monkeypatch.setenv("JEV_PROVIDER", "gemini")
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "synthetic-token")
    monkeypatch.setenv("GOOGLE_API_KEY", "synthetic-key")
    monkeypatch.setenv("JEV_ROUTING_POLICY", "priority")
    monkeypatch.setenv("JEV_MODEL_PRIORITY", '["first","second"]')
    assert settings().jev_model_priority == ("first", "second")
