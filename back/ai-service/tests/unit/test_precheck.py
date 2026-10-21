import base64
import json
from io import BytesIO
from unittest.mock import Mock

import pytest
from conftest import FORM_PAGES, FakeProvider, text_pdf
from pydantic import ValidationError
from pypdf import PdfWriter

from app.application.jev_service import JevService
from app.chains.jev_workflow import run_workflow
from app.chains.precheck import precheck
from app.schemas.jev import OUTPATIENT_DOCUMENT_KINDS, JevRequest
from scripts.generate_medical_synthetic import sample

PAGES = {kind.value: FORM_PAGES[kind.value] for kind in OUTPATIENT_DOCUMENT_KINDS}


def bundle(request_data, **changes):
    for document, kind in zip(request_data["documents"], OUTPATIENT_DOCUMENT_KINDS, strict=True):
        lines = changes.get(kind.value, PAGES[kind.value])
        raw = text_pdf(lines) if lines is not None else blank_pdf()
        document.update(kind=kind.value, content_base64=base64.b64encode(raw).decode())
    return JevRequest.model_validate(request_data)


def blank_pdf():
    writer = PdfWriter()
    writer.add_blank_page(width=300, height=300)
    stream = BytesIO()
    writer.write(stream)
    return stream.getvalue()


def service():
    provider = Mock(wraps=FakeProvider())
    provider.name, provider.model = "gemini", "fake-test-model"
    trace = Mock()
    trace.record.return_value = (None, "DISABLED")
    return JevService(provider, trace), provider


def test_complete_bundle_still_asks_the_model_once_for_the_adjuster_summary(request_data):
    runner, provider = service()
    result = runner.run(bundle(request_data))
    provider.analyze.assert_called_once()
    assert result.provider == "gemini" and result.summary
    form = json.loads(provider.analyze.call_args.args[0].form.removeprefix("JEV_TEXT_FORM="))
    assert form["server_flags"] == []
    assert all(document["missing"] == [] for document in form["documents"])
    assert result.submission_status == "ON_HOLD" and result.verification.human_review == "REQUIRED"


def test_missing_field_is_told_to_the_claimant_without_a_model_call(request_data):
    request = bundle(request_data, INITIAL_VISIT_RECORD=PAGES["INITIAL_VISIT_RECORD"][:-1])
    runner, provider = service()
    result = runner.run(request)
    provider.analyze.assert_not_called()
    assert result.provider == "rules" and result.input_tokens == result.output_tokens == 0
    assert result.verification.confidence_basis == "RULE_PRESENCE_CHECK"
    gaps = [item for item in result.items if item.judgment == "NEEDS_SUPPLEMENT"]
    assert [item.item for item in gaps] == ["초진기록지 질병분류기호"]
    assert "질병분류기호" in gaps[0].supplement and "보완이 필요한 항목이 1건" in result.summary
    assert result.risk_signals == []


def test_wrong_document_kind_is_told_to_the_claimant(request_data):
    request = bundle(request_data, MEDICAL_RECEIPT=PAGES["ITEMIZED_BILL"])
    runner, provider = service()
    result = runner.run(request)
    provider.analyze.assert_not_called()
    assert "진료비 영수증 서류 종류" in {item.item for item in result.items}


def test_model_receives_only_the_extracted_text_form(request_data):
    runner, provider = service()
    runner.run(bundle(request_data))
    sent = provider.analyze.call_args.args[0]
    form = json.loads(sent.form.removeprefix("JEV_TEXT_FORM="))
    record = form["documents"][1]
    assert record["fields"]["diagnosis"] == {"value": "가상 질환", "page": 1}
    assert record["fields"]["disease_code"] == {"value": "J20.9", "page": 1}
    assert record["fields"]["resident_id_front"]["value"] == "900101"
    assert record["fields"]["patient"]["value"] == "환자-A"
    # Never the PDF, the patient name or the back part of the resident number.
    for secret in ("JVBER", "%PDF", "테스트-김하늘", "1234567"):
        assert secret not in sent.form
    assert not any(hasattr(document, "content_base64") for document in sent.documents)


def test_cross_document_mismatch_is_flagged_for_the_model(request_data):
    receipt = [line.replace("12,300", "22,300") for line in PAGES["MEDICAL_RECEIPT"]]
    check = precheck(bundle(request_data, MEDICAL_RECEIPT=receipt))
    assert check.complete  # A suspicious but complete bundle goes to the model for the adjuster.
    assert check.flags == ("PATIENT_DUE_MISMATCH",)


def test_in_document_anomalies_are_flagged_for_the_model(request_data):
    bill = [
        *PAGES["ITEMIZED_BILL"][:-1],
        "환자부담 총액 60,000 원",
        "급여 본인부담금 10,000 원",
        "비급여 합계 20,000 원",
    ]
    record = [*PAGES["INITIAL_VISIT_RECORD"], "증상 시작일: 2026-09-05", "발급일: 2026-08-30"]
    check = precheck(bundle(request_data, ITEMIZED_BILL=bill, INITIAL_VISIT_RECORD=record))
    assert "ITEMIZED_BILL:PATIENT_DUE_EXCEEDS_GROSS" in check.flags
    assert "ITEMIZED_BILL:PATIENT_DUE_BREAKDOWN_MISMATCH" in check.flags
    assert "INITIAL_VISIT_RECORD:ONSET_AFTER_VISIT" in check.flags
    assert "INITIAL_VISIT_RECORD:ISSUE_BEFORE_VISIT" in check.flags


def test_resident_number_in_another_value_is_masked(request_data):
    bill = [*PAGES["ITEMIZED_BILL"]]
    bill[1] = "환자 성명: 테스트-김하늘 (900101-1234567)"
    form = precheck(bundle(request_data, ITEMIZED_BILL=bill)).form()
    assert "1234567" not in form


def test_image_only_bundle_is_unverifiable_without_a_model_call(request_data):
    runner, provider = service()
    request = bundle(
        request_data, ITEMIZED_BILL=None, INITIAL_VISIT_RECORD=None, MEDICAL_RECEIPT=None
    )
    result = runner.run(request)
    provider.analyze.assert_not_called()
    assert result.provider == "rules"
    assert {item.judgment for item in result.items} == {"UNVERIFIABLE"}
    assert result.summary and result.risk_signals == []


def test_one_scanned_document_is_told_to_the_claimant(request_data):
    runner, provider = service()
    result = runner.run(bundle(request_data, MEDICAL_RECEIPT=None))
    provider.analyze.assert_not_called()
    assert "진료비 영수증 판독 가능 여부" in {item.item for item in result.items}


def test_label_followed_by_other_hangul_is_not_a_match(request_data):
    record = [*PAGES["INITIAL_VISIT_RECORD"]]
    record[4] = "진료일수 3"
    check = precheck(bundle(request_data, INITIAL_VISIT_RECORD=record))
    assert "visit_date" in check.documents[1].missing


def test_outpatient_bundle_runs_through_actual_jev_stages(sample_request):
    raw = sample_request.model_dump(mode="json")
    for doc, kind in zip(raw["documents"], OUTPATIENT_DOCUMENT_KINDS, strict=True):
        doc["kind"] = kind.value
    request = JevRequest.model_validate(raw)
    assert run_workflow(request, FakeProvider()).verification.human_review == "REQUIRED"


def test_mixed_legacy_and_outpatient_bundle_is_rejected(sample_request):
    raw = sample_request.model_dump(mode="json")
    raw["documents"][0]["kind"] = "INITIAL_VISIT_RECORD"
    with pytest.raises(ValidationError):
        JevRequest.model_validate(raw)


@pytest.mark.parametrize("seed", range(10))
def test_synthetic_arithmetic_and_scenarios_are_reproducible(seed):
    data = sample(seed)
    assert data == sample(seed)
    assert data["is_synthetic"] is True
    assert sum(row["total"] for row in data["rows"]) == data["gross"]
    assert data["covered_patient"] + data["insurer"] + data["noncovered"] == data["gross"]
    assert data["patient_due"] == data["receipt_due"]
    assert sample(seed, "amount_mismatch")["receipt_due"] != data["patient_due"]
    assert sample(seed, "missing_code")["diagnosis_code"] == ""
    assert sample(seed, "patient_mismatch")["receipt_patient"] != data["patient"]
