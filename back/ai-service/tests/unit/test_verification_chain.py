from dataclasses import FrozenInstanceError
from datetime import datetime, timezone
from unittest.mock import Mock

import pytest
from conftest import FakeProvider, root_run
from pydantic import ValidationError

from app.core.config import Settings
from app.jev.structured_output import parse_findings
from app.jev.verification import VerificationChain, VerifiedResult
from app.jev.workflow import run_workflow
from app.observability.langsmith import SafeTrace
from app.providers.base import ProviderResult
from app.providers.stub_provider import StubProvider
from app.schemas.jev import JevResponse, VerificationSummary


@pytest.mark.parametrize("judgment", ["SUITABLE", "NEEDS_SUPPLEMENT", "UNVERIFIABLE"])
@pytest.mark.parametrize("confidence", [0, 0.01, 0.99, 1])
def test_structural_v_never_invents_a_confidence_cutoff(sample_request, judgment, confidence):
    findings = FakeProvider().analyze(sample_request).findings
    item = findings.items[0]
    item.judgment = judgment
    item.confidence = confidence
    if judgment == "NEEDS_SUPPLEMENT":
        item.supplement = "발급기관에 확인·정정을 요청하세요."
    draft = ProviderResult(findings, 12, 34, True)
    result = VerificationChain().run(draft)
    assert isinstance(result, VerifiedResult)
    assert result.findings.items[0].judgment == judgment
    assert result.findings.items[0].confidence == (0 if judgment == "UNVERIFIABLE" else confidence)
    assert result.verification.model_dump() == {
        "method": "STRUCTURAL",
        "factual_consistency": "NOT_VERIFIED",
        "confidence_basis": "MODEL_SELF_ASSESSMENT",
        "human_review": "REQUIRED",
    }
    assert (result.input_tokens, result.output_tokens, result.usage_complete) == (12, 34, True)
    assert draft.findings.items[0].confidence == confidence
    result.findings.items[0].verification_reason = "changed"
    assert draft.findings.items[0].verification_reason != "changed"


@pytest.mark.parametrize(
    "mutation",
    [
        "no_evidence",
        "no_supplement",
        "blank_reason",
        "nan",
        "bool_confidence",
        "string_confidence",
        "out_of_range",
        "unknown_judgment",
    ],
)
def test_v_rejects_mutated_inconsistent_drafts(sample_request, mutation):
    findings = FakeProvider().analyze(sample_request).findings
    item = findings.items[0]
    if mutation == "no_evidence":
        item.evidence = []
    elif mutation == "no_supplement":
        item.judgment = "NEEDS_SUPPLEMENT"
        item.supplement = " \t"
    elif mutation == "blank_reason":
        item.verification_reason = " \n"
    elif mutation == "nan":
        item.confidence = float("nan")
    elif mutation == "bool_confidence":
        item.confidence = True
    elif mutation == "string_confidence":
        item.confidence = "0.8"
    elif mutation == "out_of_range":
        item.confidence = 1.1
    else:
        item.judgment = "APPROVED"
    with pytest.raises(ValueError) as error:
        VerificationChain().run(ProviderResult(findings))
    assert "날짜 항목 확인" not in str(error.value)


def test_verified_result_and_summary_are_immutable(sample_request):
    result = VerificationChain().run(FakeProvider().analyze(sample_request))
    with pytest.raises(FrozenInstanceError):
        result.verification = None
    with pytest.raises(ValidationError):
        result.verification.factual_consistency = "VERIFIED"


def test_provider_cannot_choose_the_server_verification_scope(sample_request):
    findings = FakeProvider().analyze(sample_request).findings
    forged = VerifiedResult(
        findings,
        verification=VerificationSummary.model_construct(
            method="FACTUAL",
            factual_consistency="VERIFIED",
            confidence_basis="CALIBRATED",
            human_review="NOT_REQUIRED",
        ),
    )
    provider = FakeProvider()
    provider.analyze = Mock(return_value=forged)
    result = run_workflow(sample_request, provider)
    assert result.verification.factual_consistency == "NOT_VERIFIED"
    assert result.verification.human_review == "REQUIRED"
    provider.analyze.assert_called_once()


def test_model_json_cannot_include_server_verification_fields(sample_request):
    data = FakeProvider().analyze(sample_request).findings.model_dump_json()
    with pytest.raises(ValueError):
        parse_findings(data[:-1] + ',"verification":{"factual_consistency":"VERIFIED"}}')


def test_stub_discloses_no_model_assessment(sample_request):
    result = run_workflow(sample_request, StubProvider())
    assert result.verification.confidence_basis == "OFFLINE_STUB"
    assert result.verification.factual_consistency == "NOT_VERIFIED"
    assert all(
        item.judgment == "UNVERIFIABLE" and item.confidence == 0 for item in result.findings.items
    )


@pytest.mark.parametrize("has_evidence", [False, True])
def test_stub_cannot_be_promoted_to_a_real_recommendation(sample_request, has_evidence):
    findings = FakeProvider().analyze(sample_request).findings
    if has_evidence:
        findings.items[0].judgment = "UNVERIFIABLE"
    else:
        findings.items[0].evidence = []
    with pytest.raises(ValueError):
        VerificationChain().run(ProviderResult(findings), offline_stub=True)


def test_safe_trace_adds_only_verification_constants_on_success(monkeypatch, sample_request):
    client = Mock()
    monkeypatch.setattr("langsmith.Client", Mock(return_value=client))
    result = VerificationChain().run(FakeProvider().analyze(sample_request))
    trace = SafeTrace(
        Settings(app_env="local", langsmith_enabled=True, langsmith_api_key="synthetic")
    )
    _, status = trace.record(
        datetime.now(timezone.utc), "gemini", "fake", True, verification=result.verification
    )
    assert status == "RECORDED"
    payload = root_run(client)
    assert payload["inputs"] == payload["outputs"] == {}
    for key, value in result.verification.model_dump().items():
        assert payload["extra"]["metadata"][f"verification_{key}"] == value
    assert "날짜 항목 확인" not in str(payload)
    trace.record(
        datetime.now(timezone.utc), "gemini", "fake", False, verification=result.verification
    )
    assert not any(key.startswith("verification_") for key in root_run(client)["extra"]["metadata"])


def test_forged_verification_cannot_leak_through_trace(monkeypatch):
    client = Mock()
    monkeypatch.setattr("langsmith.Client", Mock(return_value=client))
    forged = VerificationSummary.model_construct(method="SECRET_PATIENT")
    trace = SafeTrace(
        Settings(app_env="local", langsmith_enabled=True, langsmith_api_key="synthetic")
    )
    _, status = trace.record(
        datetime.now(timezone.utc), "gemini", "fake", True, verification=forged
    )
    assert status == "FAILED"
    client.create_run.assert_not_called()


def test_response_basis_must_match_provider(sample_request):
    from app.application.jev_service import JevService

    trace = Mock()
    trace.record.return_value = (None, "DISABLED")
    result = JevService(FakeProvider(), trace).run(sample_request)
    payload = result.model_dump(mode="json")
    payload["verification"]["confidence_basis"] = "OFFLINE_STUB"
    with pytest.raises(ValueError):
        JevResponse.model_validate(payload)
