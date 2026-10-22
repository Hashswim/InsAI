from unittest.mock import Mock

import pytest
from conftest import FakeProvider, root_run
from httpx import ReadTimeout

from app.application.jev_service import JevService
from app.core.config import Settings
from app.core.errors import ProviderFailure
from app.jev.workflow import run_workflow
from app.observability.langsmith import SafeTrace
from app.schemas.jev import Item


def test_regulations_cannot_be_approved(sample_request):
    trace = Mock()
    trace.record.return_value = (None, "DISABLED")
    result = JevService(FakeProvider(), trace).run(sample_request)
    assert result.submission_status == "ON_HOLD"
    assert result.regulations_status == "NOT_REGISTERED"
    assert result.items[0].evidence[0].page == 1


def test_invalid_evidence_page_rejected(sample_request):
    provider = FakeProvider()
    response = provider.analyze(sample_request)
    response.findings.items[0].evidence[0].page = 99
    provider.analyze = Mock(return_value=response)
    with pytest.raises(ValueError):
        run_workflow(sample_request, provider)


def test_sensitive_provider_exception_is_hidden(sample_request):
    provider = FakeProvider()
    provider.analyze = Mock(side_effect=RuntimeError("SECRET_PATIENT"))
    trace = Mock()
    with pytest.raises(ProviderFailure) as error:
        JevService(provider, trace).run(sample_request)
    assert "SECRET_PATIENT" not in str(error.value)
    assert "SECRET_PATIENT" not in str(trace.mock_calls)


def test_suitable_requires_evidence():
    with pytest.raises(ValueError):
        Item(
            item="date",
            judgment="SUITABLE",
            evidence=[],
            confidence=0.9,
            verification_reason="present",
            supplement="",
        )


@pytest.mark.parametrize("timeout_type", [TimeoutError, ReadTimeout])
def test_builtin_timeout_is_classified_without_raw_error(sample_request, timeout_type):
    from app.core.errors import ProviderTimeout

    provider = FakeProvider()
    provider.analyze = Mock(side_effect=timeout_type("SENTINEL_TIMEOUT"))
    trace = Mock()
    with pytest.raises(ProviderTimeout) as error:
        JevService(provider, trace).run(sample_request)
    assert "SENTINEL" not in str(error.value)
    assert provider.analyze.call_count == 1


def test_invalid_model_output_is_classified(sample_request):
    from app.core.errors import InvalidProviderResponse

    provider = FakeProvider()
    provider.analyze = Mock(side_effect=ValueError("SENTINEL_INVALID_JSON"))
    with pytest.raises(InvalidProviderResponse) as error:
        JevService(provider, Mock()).run(sample_request)
    assert "SENTINEL" not in str(error.value)


def test_trace_only_receives_allowlisted_metadata(monkeypatch):
    client = Mock()
    monkeypatch.setattr("langsmith.Client", Mock(return_value=client))
    from datetime import datetime, timezone

    trace_id, status = SafeTrace(
        Settings(app_env="local", langsmith_enabled=True, langsmith_api_key="test-trace-key")
    ).record(datetime.now(timezone.utc), "gemini", "test-model", True, 10, 20)
    payload = root_run(client)
    assert trace_id and status == "RECORDED"
    assert payload["inputs"] == payload["outputs"] == {}
    assert set(payload["extra"]["metadata"]) == {
        "provider",
        "model",
        "prompt_version",
        "prompt_sha256",
        "chain_version",
        "input_tokens",
        "output_tokens",
    }


def test_tracing_failure_does_not_fail_analysis(monkeypatch):
    monkeypatch.setattr("langsmith.Client", Mock(side_effect=RuntimeError("SECRET")))
    from datetime import datetime, timezone

    assert SafeTrace(
        Settings(app_env="local", langsmith_enabled=True, langsmith_api_key="test-trace-key")
    ).record(datetime.now(timezone.utc), "gemini", "test-model", True) == (None, "FAILED")
