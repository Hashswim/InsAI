from datetime import datetime, timezone
from unittest.mock import Mock

import pytest

from app.application.resilient_execution import ExecutionStats
from app.core.config import Settings
from app.observability.evaluation import evaluate_synthetic
from app.observability.langsmith import SafeTrace
from app.observability.metadata import checked_metadata


@pytest.mark.parametrize(
    "metadata",
    [
        {"patient": "SENTINEL_PATIENT"},
        {"model": "SENTINEL patient name"},
        {"provider": "unapproved"},
        {"input_tokens": True},
        {"input_tokens": -1},
        {"input_tokens": 1.5},
        {"input_tokens": 2**64},
        {"cost_estimate_usd": "NaN"},
        {"cost_estimate_usd": "-1"},
        {"verification_factual_consistency": "VERIFIED"},
        {"last_workflow_failed_stage": "SENTINEL"},
        {"last_workflow_completed_stages": "E,J"},
    ],
)
def test_unapproved_metadata_is_rejected(metadata):
    with pytest.raises(ValueError):
        checked_metadata(metadata)


@pytest.mark.parametrize("key", ["patient", "model", "provider"])
def test_extension_cannot_send_new_fields(monkeypatch, key):
    factory = Mock()
    monkeypatch.setattr("langsmith.Client", factory)
    monkeypatch.setattr(ExecutionStats, "metadata", lambda self: {key: "SENTINEL"})
    config = Settings(app_env="local", langsmith_enabled=True, langsmith_api_key="test-key")
    assert SafeTrace(config).record(
        datetime.now(timezone.utc), "gemini", "test-model", True, execution=ExecutionStats()
    ) == (None, "FAILED")
    factory.assert_not_called()


def test_real_sdk_does_not_add_runtime_or_environment_metadata(monkeypatch):
    from langsmith import Client

    captured = []
    monkeypatch.setenv("LANGCHAIN_REVISION_ID", "SENTINEL_PRIVATE_ENV")
    monkeypatch.setattr("requests.Session.request", Mock(side_effect=AssertionError("no network")))
    monkeypatch.setattr(Client, "_create_run", lambda self, data, **kwargs: captured.append(data))
    config = Settings(
        app_env="local",
        langsmith_enabled=True,
        langsmith_api_key="test-key",
        langsmith_project="test-project",
    )
    trace_id, status = SafeTrace(config).record(
        datetime.now(timezone.utc), "gemini", "test-model", True
    )
    assert status == "RECORDED" and trace_id
    assert len(captured) == 1
    payload = captured[0]
    assert payload["inputs"] == payload["outputs"] == {}
    assert set(payload["extra"]) == {"metadata"}
    assert "SENTINEL" not in str(payload)
    assert "revision_id" not in payload["extra"]["metadata"]


def test_synthetic_evaluation_never_uses_network_or_trace(monkeypatch):
    monkeypatch.setattr("requests.Session.request", Mock(side_effect=AssertionError("no network")))
    trace = Mock(side_effect=AssertionError("no telemetry"))
    monkeypatch.setattr("langsmith.Client", trace)
    report = evaluate_synthetic()
    assert report["mode"] == "OFFLINE_SYNTHETIC"
    assert report["total"] == report["passed"] == 5
    assert "document_id" not in str(report)
    assert "confidence" not in str(report)
    trace.assert_not_called()
