import json
from uuid import uuid4

import pytest
from conftest import FakeProvider

from app.jev.structured_output import (
    INVALID_OUTPUT,
    MAX_FINDINGS_BYTES,
    MAX_JSON_DEPTH,
    decode_json_object,
    parse_findings,
    validate_findings,
)
from app.jev.workflow import run_workflow


@pytest.fixture
def findings_data(sample_request):
    return FakeProvider().analyze(sample_request).findings.model_dump(mode="json")


def test_valid_json_is_copied_and_evidence_preserved(findings_data):
    result = parse_findings(" \n" + json.dumps(findings_data, ensure_ascii=False) + "\t")
    assert result.model_dump(mode="json") == findings_data
    copy = validate_findings(result)
    copy.items[0].item = "changed"
    assert result.items[0].item != "changed"


@pytest.mark.parametrize(
    "text",
    [
        None,
        {},
        b"{}",
        "",
        "SECRET_PATIENT",
        "```json\n{}\n```",
        "Before {}",
        "{} after",
        "{}{}",
        '{"items":',
        '{"items":[],}',
        '/*comment*/{"items":[]}',
        '{"items":[],"items":[]}',
        '{"items":[],"\\u0069tems":[]}',
        '{"nested":{"page":1,"page":2}}',
        '{"number":NaN}',
        '{"number":Infinity}',
        '{"number":-Infinity}',
        '{"number":1e999}',
        "[]",
        "null",
        '"text"',
        "true",
        "1",
        "\ufeff{}",
        '{"text":"\ud800"}',
    ],
)
def test_malformed_json_is_rejected_safely(text):
    with pytest.raises(ValueError) as error:
        parse_findings(text)
    assert str(error.value) == INVALID_OUTPUT
    assert error.value.__suppress_context__


def test_nesting_and_byte_limits_are_checked_before_decoding():
    assert decode_json_object('{"text":"[\\"{}]"}') == {"text": '["{}]'}
    text = '{"nested":' + "[" * MAX_JSON_DEPTH + "0" + "]" * MAX_JSON_DEPTH + "}"
    with pytest.raises(ValueError, match=INVALID_OUTPUT):
        decode_json_object(text)
    with pytest.raises(ValueError, match=INVALID_OUTPUT):
        decode_json_object(" " * (MAX_FINDINGS_BYTES + 1))
    with pytest.raises(ValueError, match=INVALID_OUTPUT):
        decode_json_object('{"text":"' + "한" * (MAX_FINDINGS_BYTES // 2) + '"}')
    assert decode_json_object("{}", max_bytes=2) == {}


@pytest.mark.parametrize("page", [True, False, "1", 1.0, 1.5, None, 0, -1])
def test_page_requires_positive_json_integer(findings_data, page):
    findings_data["items"][0]["evidence"][0]["page"] = page
    with pytest.raises(ValueError, match=INVALID_OUTPUT):
        parse_findings(json.dumps(findings_data))


@pytest.mark.parametrize("confidence", [True, False, "0.8", None, -0.1, 1.1, float("nan")])
def test_confidence_requires_finite_json_number(findings_data, confidence):
    findings_data["items"][0]["confidence"] = confidence
    with pytest.raises(ValueError, match=INVALID_OUTPUT):
        parse_findings(json.dumps(findings_data))


@pytest.mark.parametrize("confidence", [0, 1, 0.8])
def test_integer_or_fractional_confidence_is_valid(findings_data, confidence):
    findings_data["items"][0]["confidence"] = confidence
    assert parse_findings(json.dumps(findings_data)).items[0].confidence == confidence


@pytest.mark.parametrize(
    "mutation",
    [
        "empty_items",
        "too_many_items",
        "too_many_evidence",
        "long_item",
        "blank_item",
        "blank_reason",
        "blank_description",
        "unknown_judgment",
        "missing_field",
        "extra_field",
        "extra_item_field",
        "extra_evidence_field",
        "null_evidence",
        "unjustified",
        "no_supplement",
        "surrogate",
        "wrong_uuid",
    ],
)
def test_findings_schema_and_invariants(findings_data, mutation):
    item = findings_data["items"][0]
    evidence = item["evidence"][0]
    if mutation == "empty_items":
        findings_data["items"] = []
    elif mutation == "too_many_items":
        findings_data["items"] *= 101
    elif mutation == "too_many_evidence":
        item["evidence"] *= 21
    elif mutation == "long_item":
        item["item"] = "x" * 201
    elif mutation == "blank_item":
        item["item"] = " \t\n"
    elif mutation == "blank_reason":
        item["verification_reason"] = "  "
    elif mutation == "blank_description":
        evidence["description"] = "  "
    elif mutation == "unknown_judgment":
        item["judgment"] = "APPROVED"
    elif mutation == "missing_field":
        item.pop("supplement")
    elif mutation == "extra_field":
        findings_data["submission_status"] = "APPROVED"
    elif mutation == "extra_item_field":
        item["SECRET_PATIENT"] = "extra"
    elif mutation == "extra_evidence_field":
        evidence["url"] = "https://untrusted.invalid"
    elif mutation == "null_evidence":
        item["evidence"] = None
    elif mutation == "unjustified":
        item["evidence"] = []
    elif mutation == "no_supplement":
        item["judgment"] = "NEEDS_SUPPLEMENT"
        item["supplement"] = " \n"
    elif mutation == "surrogate":
        item["supplement"] = "\ud800"
    elif mutation == "wrong_uuid":
        evidence["document_id"] = "not-a-uuid"
    with pytest.raises(ValueError, match=INVALID_OUTPUT):
        parse_findings(json.dumps(findings_data))


def test_unverifiable_still_normalizes_confidence_to_zero(findings_data):
    item = findings_data["items"][0]
    item.update(judgment="UNVERIFIABLE", confidence=0.8, evidence=[])
    assert parse_findings(json.dumps(findings_data)).items[0].confidence == 0


def test_workflow_revalidates_mutated_provider_models(sample_request):
    provider = FakeProvider()
    result = provider.analyze(sample_request)
    result.findings.items[0].evidence[0].page = "1"
    provider.analyze = lambda request: result
    with pytest.raises(ValueError, match=INVALID_OUTPUT):
        run_workflow(sample_request, provider)


def test_typed_sdk_results_cannot_bypass_the_total_size_limit(findings_data):
    item = findings_data["items"][0]
    item["evidence"][0]["description"] = "x" * 2000
    item["evidence"] *= 20
    findings_data["items"] *= 100
    with pytest.raises(ValueError, match=INVALID_OUTPUT):
        validate_findings(findings_data)


def test_syntactically_valid_unknown_document_is_rejected_by_workflow(sample_request):
    provider = FakeProvider()
    result = provider.analyze(sample_request)
    result.findings.items[0].evidence[0].document_id = uuid4()
    provider.analyze = lambda request: result
    with pytest.raises(ValueError, match="unknown document or page"):
        run_workflow(sample_request, provider)
