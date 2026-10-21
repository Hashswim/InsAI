import base64
import json
from io import BytesIO
from unittest.mock import Mock

import pytest
from conftest import FakeProvider, text_pdf
from pydantic import ValidationError
from pypdf import PdfWriter

from app.application.jev_service import JevService
from app.chains.jev_workflow import run_workflow
from app.chains.precheck import precheck
from app.schemas.jev import OUTPATIENT_DOCUMENT_KINDS, JevRequest
from scripts.generate_medical_synthetic import sample

COMMON = [
    "환자 성명: 테스트-김하늘",
    "주민등록번호: 900101-1234567",
    "의료기관: 가상 테스트의원",
    "진료일: 2026-09-01",
]
PAGES = {
    "ITEMIZED_BILL": [
        "진료비 세부산정내역서",
        *COMMON,
        "진료비 총액 50,000 원",
        "환자부담 총액 12,300 원",
    ],
    "INITIAL_VISIT_RECORD": [
        "초진기록지",
        *COMMON,
        "주호소: 가상 증상",
        "현병력: 3일 전 시작",
        "진단명: 가상 질환",
        "질병분류기호: J20.9",
    ],
    "MEDICAL_RECEIPT": [
        "진료비 계산서·영수증",
        *COMMON,
        "진료비 총액 50,000 원",
        "환자부담 총액 12,300 원",
        "납부액 12,300 원",
    ],
}


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


def test_complete_bundle_is_answered_by_rules_without_a_model_call(request_data):
    runner, provider = service()
    result = runner.run(bundle(request_data))
    provider.analyze.assert_not_called()
    assert result.provider == "rules" and result.input_tokens == result.output_tokens == 0
    assert result.verification.confidence_basis == "RULE_PRESENCE_CHECK"
    assert result.submission_status == "ON_HOLD" and result.verification.human_review == "REQUIRED"
    assert {item.judgment for item in result.items} == {"SUITABLE"}
    assert "환자부담 총액 일치(세부내역서·영수증)" in {item.item for item in result.items}
    assert "테스트-김하늘" not in result.model_dump_json()
    assert "900101" not in result.model_dump_json()


def test_missing_field_sends_only_the_extracted_text_form(request_data):
    request = bundle(request_data, INITIAL_VISIT_RECORD=PAGES["INITIAL_VISIT_RECORD"][:-1])
    runner, provider = service()
    assert runner.run(request).provider == "gemini"
    sent = provider.analyze.call_args.args[0]
    form = json.loads(sent.form.removeprefix("JEV_TEXT_FORM="))
    record = form["documents"][1]
    assert record["missing"] == ["disease_code"]
    assert record["fields"]["diagnosis"] == {"value": "가상 질환", "page": 1}
    assert record["fields"]["resident_id_front"]["value"] == "900101"
    assert record["fields"]["patient"]["value"] == "환자-A"
    # Never the PDF, the patient name or the back part of the resident number.
    for secret in ("JVBER", "%PDF", "테스트-김하늘", "1234567"):
        assert secret not in sent.form
    assert not any(hasattr(document, "content_base64") for document in sent.documents)


def test_cross_document_mismatch_goes_to_the_model_with_a_flag(request_data):
    receipt = [line.replace("12,300", "22,300") for line in PAGES["MEDICAL_RECEIPT"]]
    check = precheck(bundle(request_data, MEDICAL_RECEIPT=receipt))
    assert all(document.reliable for document in check.documents)
    assert check.mismatches == ("PATIENT_DUE_MISMATCH",)
    assert not check.confident


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
