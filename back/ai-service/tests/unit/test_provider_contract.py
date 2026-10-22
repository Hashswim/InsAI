from dataclasses import FrozenInstanceError
from unittest.mock import Mock

import pytest
from conftest import FakeProvider

from app.application.jev_service import JevService
from app.core.config import Settings
from app.core.errors import InvalidProviderResponse, ProviderFailure, ProviderTimeout
from app.jev.workflow import run_workflow
from app.providers.base import Provider, ProviderRequest, ProviderResult
from app.providers.router import build_provider


def test_workflow_maps_http_input_to_private_port(sample_request):
    provider = FakeProvider()
    original = provider.analyze
    provider.analyze = Mock(side_effect=original)
    result = run_workflow(sample_request, provider)
    received = provider.analyze.call_args.args[0]
    assert isinstance(received, ProviderRequest)
    assert received.request_id == sample_request.request_id
    assert isinstance(received.documents, tuple)
    # The port carries document identity and the extracted text form, never PDF bytes.
    assert not hasattr(received.documents[0], "content_base64")
    assert received.form.startswith("JEV_TEXT_FORM=")
    assert sample_request.documents[0].content_base64 not in received.form
    assert received.form not in repr(received)
    assert "날짜 항목 확인" not in repr(result)
    with pytest.raises(FrozenInstanceError):
        received.request_id = sample_request.request_id


@pytest.mark.parametrize("count", [-1, True, 1.5, "10", None])
def test_invalid_usage_is_rejected(sample_request, count):
    findings = FakeProvider().analyze(sample_request).findings
    with pytest.raises(ValueError, match="Invalid provider token usage"):
        ProviderResult(findings, input_tokens=count)
    with pytest.raises(ValueError):
        ProviderResult(findings, output_tokens=count)


@pytest.mark.parametrize("error_type", [ProviderTimeout, InvalidProviderResponse, ProviderFailure])
def test_portable_error_category_survives_without_raw_text(sample_request, error_type):
    provider = FakeProvider()
    provider.analyze = Mock(side_effect=error_type("SECRET_PATIENT"))
    trace = Mock()
    with pytest.raises(error_type) as error:
        JevService(provider, trace).run(sample_request)
    assert "SECRET_PATIENT" not in str(error.value)
    provider.analyze.assert_called_once()
    assert trace.record.call_args.args[3] is False


def test_stub_satisfies_port_and_default_usage(sample_request):
    provider = build_provider(Settings(jev_provider="stub"))
    assert isinstance(provider, Provider)
    result = run_workflow(sample_request, provider)
    assert result.input_tokens == result.output_tokens == 0
    assert len(result.findings.items) == 3


def test_wrong_adapter_result_is_invalid(sample_request):
    provider = FakeProvider()
    provider.analyze = Mock(return_value={"findings": "not a port result"})
    with pytest.raises(ValueError, match="Invalid provider result type"):
        run_workflow(sample_request, provider)
