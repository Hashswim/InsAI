import base64
from io import BytesIO
from unittest.mock import Mock
from uuid import uuid4

import pytest
from conftest import FakeProvider
from pypdf import PdfWriter

from app.chains.evidence import EvidenceChain
from app.chains.jev_workflow import run_workflow
from app.providers.base import ProviderResult
from app.schemas.jev import JevRequest


def multipage_request(request_data, count):
    writer = PdfWriter()
    for _ in range(count):
        writer.add_blank_page(width=300, height=300)
    stream = BytesIO()
    writer.write(stream)
    request_data["documents"][0]["content_base64"] = base64.b64encode(stream.getvalue()).decode()
    return JevRequest.model_validate(request_data)


def test_collects_citations_without_mutating_the_model_draft(sample_request):
    findings = FakeProvider().analyze(sample_request).findings
    draft = ProviderResult(findings, 10, 20, True)
    collected = EvidenceChain().run(sample_request, draft)
    assert collected.findings == findings
    assert collected.findings is not findings
    assert (collected.input_tokens, collected.output_tokens, collected.usage_complete) == (
        10,
        20,
        True,
    )
    collected.findings.items[0].evidence[0].description = "changed"
    assert draft.findings.items[0].evidence[0].description != "changed"
    assert "날짜 항목 확인" not in repr(collected)


@pytest.mark.parametrize("mutation", ["unknown_document", "page_outside_pdf"])
def test_rejects_unknown_references_even_for_unverifiable(sample_request, mutation):
    findings = FakeProvider().analyze(sample_request).findings
    findings.items[0].judgment = "UNVERIFIABLE"
    evidence = findings.items[0].evidence[0]
    if mutation == "unknown_document":
        evidence.document_id = uuid4()
    else:
        evidence.page = 2
    with pytest.raises(ValueError, match="unknown document or page"):
        EvidenceChain().run(sample_request, ProviderResult(findings))


@pytest.mark.parametrize("count", [1, 2, 30])
def test_last_actual_pdf_page_is_valid(request_data, count):
    request = multipage_request(request_data, count)
    findings = FakeProvider().analyze(request).findings
    findings.items[0].evidence[0].page = count
    result = EvidenceChain().run(request, ProviderResult(findings))
    assert result.findings.items[0].evidence[0].page == count


def test_one_documents_page_count_does_not_authorize_another(request_data):
    request = multipage_request(request_data, 2)
    findings = FakeProvider().analyze(request).findings
    findings.items[0].evidence[0].document_id = request.documents[1].document_id
    findings.items[0].evidence[0].page = 2
    with pytest.raises(ValueError, match="unknown document or page"):
        EvidenceChain().run(request, ProviderResult(findings))


@pytest.mark.parametrize("whitespace", [False, True])
def test_duplicate_evidence_fails_without_dropping_or_filling(sample_request, whitespace):
    findings = FakeProvider().analyze(sample_request).findings
    duplicate = findings.items[0].evidence[0].model_copy(deep=True)
    if whitespace:
        duplicate.description = "  날짜  항목\n확인 \t"
    findings.items[0].evidence.append(duplicate)
    with pytest.raises(ValueError) as error:
        EvidenceChain().run(sample_request, ProviderResult(findings))
    assert str(error.value) == "Duplicate E-stage evidence"
    assert len(findings.items[0].evidence) == 2


@pytest.mark.parametrize("difference", ["description", "document", "page", "case"])
def test_distinct_observations_are_preserved(request_data, difference):
    request = multipage_request(request_data, 2)
    findings = FakeProvider().analyze(request).findings
    evidence = findings.items[0].evidence[0]
    evidence.description = "Observed date"
    other = evidence.model_copy(deep=True)
    if difference == "description":
        other.description = "Observed institution"
    elif difference == "document":
        other.document_id = request.documents[1].document_id
    elif difference == "page":
        other.page = 2
    else:
        other.description = "observed date"
    findings.items[0].evidence.append(other)
    result = EvidenceChain().run(request, ProviderResult(findings))
    assert len(result.findings.items[0].evidence) == 2


def test_shared_citation_can_support_separate_items(sample_request):
    findings = FakeProvider().analyze(sample_request).findings
    other = findings.items[0].model_copy(deep=True)
    other.item = "발급기관 기재"
    findings.items.append(other)
    result = EvidenceChain().run(sample_request, ProviderResult(findings))
    assert len(result.findings.items) == 2


def test_both_sides_of_a_comparison_are_preserved(sample_request):
    findings = FakeProvider().analyze(sample_request).findings
    item = findings.items[0]
    item.judgment = "NEEDS_SUPPLEMENT"
    item.supplement = "발급기관에 날짜 확인을 요청하세요."
    other = item.evidence[0].model_copy(deep=True)
    other.document_id = sample_request.documents[1].document_id
    item.evidence.append(other)
    result = EvidenceChain().run(sample_request, ProviderResult(findings))
    assert [e.document_id for e in result.findings.items[0].evidence] == [
        sample_request.documents[0].document_id,
        sample_request.documents[1].document_id,
    ]


@pytest.mark.parametrize("judgment", ["SUITABLE", "NEEDS_SUPPLEMENT", "UNVERIFIABLE"])
def test_missing_evidence_is_not_invented(sample_request, judgment):
    findings = FakeProvider().analyze(sample_request).findings
    item = findings.items[0]
    item.judgment = judgment
    item.evidence = []
    item.supplement = "발급기관에 확인하세요."
    if judgment == "UNVERIFIABLE":
        result = EvidenceChain().run(sample_request, ProviderResult(findings))
        assert result.findings.items[0].evidence == []
        assert result.findings.items[0].confidence == 0
    else:
        with pytest.raises(ValueError):
            EvidenceChain().run(sample_request, ProviderResult(findings))


@pytest.mark.parametrize("page", [True, "1", 1.0, 0, -1])
def test_e_revalidates_mutated_sdk_evidence(sample_request, page):
    findings = FakeProvider().analyze(sample_request).findings
    findings.items[0].evidence[0].page = page
    with pytest.raises(ValueError):
        EvidenceChain().run(sample_request, ProviderResult(findings))


def test_pdf_index_error_is_sanitized(monkeypatch, sample_request):
    draft = FakeProvider().analyze(sample_request)
    monkeypatch.setattr(
        "app.schemas.jev.PdfDocument.page_count", Mock(side_effect=OSError("SECRET_PDF"))
    )
    with pytest.raises(ValueError) as error:
        EvidenceChain().run(sample_request, draft)
    assert str(error.value) == "E-stage PDF reference index is unavailable"
    assert error.value.__suppress_context__


@pytest.mark.parametrize("count", [0, -1, 31, True, 1.5, "1"])
def test_invalid_pdf_index_counts_are_not_trusted(monkeypatch, sample_request, count):
    draft = FakeProvider().analyze(sample_request)
    monkeypatch.setattr("app.schemas.jev.PdfDocument.page_count", Mock(return_value=count))
    with pytest.raises(ValueError, match="E-stage PDF reference index is unavailable"):
        EvidenceChain().run(sample_request, draft)


def test_bad_draft_is_rejected_without_pdf_access(monkeypatch, sample_request):
    pages = Mock()
    monkeypatch.setattr("app.schemas.jev.PdfDocument.page_count", pages)
    with pytest.raises(ValueError, match="Invalid E-stage draft"):
        EvidenceChain().run(sample_request, {"findings": "SECRET_PATIENT"})
    pages.assert_not_called()


def test_rejects_the_whole_draft_when_one_item_has_a_bad_reference(sample_request):
    findings = FakeProvider().analyze(sample_request).findings
    bad = findings.items[0].model_copy(deep=True)
    bad.item = "different item"
    bad.evidence[0].page = 2
    findings.items.append(bad)
    with pytest.raises(ValueError, match="unknown document or page"):
        EvidenceChain().run(sample_request, ProviderResult(findings))
    assert findings.items[0].evidence[0].page == 1


def test_duplicate_document_ids_are_rejected_before_indexing(monkeypatch, sample_request):
    draft = FakeProvider().analyze(sample_request)
    sample_request.documents[1].document_id = sample_request.documents[0].document_id
    pages = Mock()
    monkeypatch.setattr("app.schemas.jev.PdfDocument.page_count", pages)
    with pytest.raises(ValueError, match="Invalid E-stage document scope"):
        EvidenceChain().run(sample_request, draft)
    pages.assert_not_called()


def test_workflow_calls_model_once_and_preserves_evidence(sample_request):
    provider = FakeProvider()
    provider.analyze = Mock(wraps=provider.analyze)
    result = run_workflow(sample_request, provider)
    assert result.findings.items[0].evidence[0].page == 1
    provider.analyze.assert_called_once()
