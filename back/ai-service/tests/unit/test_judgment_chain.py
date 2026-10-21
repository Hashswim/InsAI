from dataclasses import replace
from unittest.mock import Mock

import pytest
from conftest import FakeProvider
from langsmith.run_helpers import get_tracing_context

from app.chains.jev_workflow import run_workflow
from app.chains.judgment import JudgmentChain
from app.chains.precheck import precheck
from app.core.errors import InvalidProviderResponse, ProviderTimeout, TransientProviderFailure
from app.core.resources import load_jev_prompt
from app.providers.base import ProviderDocument, ProviderRequest, ProviderResult
from app.schemas.jev import Findings


def port_request(request):
    return ProviderRequest(
        request.request_id,
        tuple(ProviderDocument(d.document_id, d.kind) for d in request.documents),
        precheck(request).form(),
    )


def candidate(request, case):
    """Controlled model drafts, NOT PDF interpretation or accuracy fixtures."""
    data = FakeProvider().analyze(request).findings.model_dump(mode="json")
    item = data["items"][0]
    if case == "C02":
        item.update(
            item="동일 진료일 일치",
            judgment="NEEDS_SUPPLEMENT",
            verification_reason="동일 방문의 진료일자가 다름",
            supplement="발급기관에 동일 방문인지 확인하고 날짜 정정을 요청하세요.",
        )
        item["evidence"] = [
            {"document_id": str(d.document_id), "page": 1, "description": "비교한 진료일자가 다름"}
            for d in request.documents[1:]
        ]
    elif case == "C03":
        item.update(
            item="진단 내용 판독",
            judgment="UNVERIFIABLE",
            evidence=[],
            confidence=0.8,
            verification_reason="진단 내용이 흐려 판독할 수 없음",
            supplement="선명한 진단서를 재발급받아 확인하세요.",
        )
    elif case == "C04":
        item.update(
            item="진단 내용 작성",
            judgment="NEEDS_SUPPLEMENT",
            verification_reason="선명한 인쇄 항목이 공란임",
            supplement="발급기관에 진단 내용란의 작성 보완을 요청하세요.",
        )
        item["evidence"][0]["description"] = "선명한 인쇄 진단 내용란이 공란임"
    elif case == "C05":
        item.update(
            item="진료확인서 종류",
            judgment="NEEDS_SUPPLEMENT",
            verification_reason="지정 파일은 영수증으로 관찰됨",
            supplement="파일을 확인하고 진료확인서로 교체하세요.",
        )
        item["evidence"][0].update(
            document_id=str(request.documents[1].document_id),
            description="영수증 제목과 수납 항목만 확인됨",
        )
        data["items"].append(
            {
                **item,
                "item": "진료 내용",
                "judgment": "UNVERIFIABLE",
                "confidence": 0,
                "evidence": [],
                "verification_reason": "진료확인서 내용이 없어 판단할 수 없음",
            }
        )
    return Findings.model_validate(data)


@pytest.mark.parametrize("case", ["C01", "C02", "C03", "C04", "C05"])
def test_synthetic_drafts_preserve_recommendation_and_make_one_call(sample_request, case):
    draft = candidate(sample_request, case)
    provider = FakeProvider()
    provider.analyze = Mock(return_value=ProviderResult(draft, 12, 34, True))
    result = JudgmentChain(provider).run(port_request(sample_request))
    assert result.findings.model_dump() == draft.model_dump()
    assert result.findings is not draft
    assert (result.input_tokens, result.output_tokens, result.usage_complete) == (12, 34, True)
    provider.analyze.assert_called_once()
    assert "진단" not in repr(result)
    if case == "C03":
        assert result.findings.items[0].judgment == "UNVERIFIABLE"
        assert result.findings.items[0].confidence == 0
    if case == "C05":
        assert [item.judgment for item in result.findings.items] == [
            "NEEDS_SUPPLEMENT",
            "UNVERIFIABLE",
        ]


@pytest.mark.parametrize("mutation", ["missing", "duplicate_id", "duplicate_kind", "wrong_type"])
def test_bad_document_scope_fails_before_provider(sample_request, mutation):
    request = port_request(sample_request)
    documents = request.documents
    if mutation == "missing":
        request = replace(request, documents=documents[:2])
    elif mutation == "duplicate_id":
        request = replace(request, documents=(documents[0], documents[0], documents[2]))
    elif mutation == "duplicate_kind":
        request = replace(
            request,
            documents=(documents[0], replace(documents[1], kind=documents[0].kind), documents[2]),
        )
    elif mutation == "wrong_type":
        request = {"documents": "SECRET_PATIENT"}
    provider = Mock()
    with pytest.raises(ValueError, match="Invalid J-stage document scope"):
        JudgmentChain(provider).run(request)
    provider.analyze.assert_not_called()


@pytest.mark.parametrize("contradictory", [False, True])
def test_duplicate_scope_is_rejected_without_merging(sample_request, contradictory):
    findings = FakeProvider().analyze(sample_request).findings
    duplicate = findings.items[0].model_copy(deep=True)
    duplicate.item = " \n" + duplicate.item + "\t "
    if contradictory:
        duplicate.judgment = "NEEDS_SUPPLEMENT"
        duplicate.supplement = "SECRET_PATIENT"
    findings.items.append(duplicate)
    provider = FakeProvider()
    provider.analyze = Mock(return_value=ProviderResult(findings))
    with pytest.raises(ValueError) as error:
        run_workflow(sample_request, provider)
    assert str(error.value) == "Duplicate J-stage judgment scope"
    assert "SECRET" not in str(error.value)
    assert len(findings.items) == 2
    provider.analyze.assert_called_once()


def test_same_label_for_different_documents_is_not_a_duplicate(sample_request):
    findings = FakeProvider().analyze(sample_request).findings
    other = findings.items[0].model_copy(deep=True)
    other.evidence[0].document_id = sample_request.documents[1].document_id
    findings.items.append(other)
    provider = FakeProvider()
    provider.analyze = Mock(return_value=ProviderResult(findings))
    assert len(run_workflow(sample_request, provider).findings.items) == 2


def test_direct_j_invocation_disables_automatic_tracing(sample_request):
    provider = FakeProvider()
    original = provider.analyze

    def analyze(request):
        assert get_tracing_context()["enabled"] is False
        return original(request)

    provider.analyze = analyze
    JudgmentChain(provider).run(port_request(sample_request))


@pytest.mark.parametrize(
    "error", [ProviderTimeout(), TransientProviderFailure(), InvalidProviderResponse()]
)
def test_j_does_not_retry_or_reclassify_provider_errors(sample_request, error):
    provider = Mock()
    provider.analyze.side_effect = error
    with pytest.raises(type(error)) as caught:
        JudgmentChain(provider).run(port_request(sample_request))
    assert caught.value is error
    provider.analyze.assert_called_once()


def test_prompt_contains_medical_judgment_distinctions():
    prompt = load_jev_prompt()
    for text in (
        "J — Judgment",
        "PDF 원본은 전달되지 않습니다",
        "JEV_TEXT_FORM",
        "추출 실패와 공란을 구분",
        "발급일과 진료일",
        "의미가 불명확하면 UNVERIFIABLE",
        "의학적 필요성은 판단하지",
        "영수증",
        "양쪽 문서",
        "비신뢰 문서 텍스트",
        "스키마만 반환",
        "주민등록번호 앞자리 값을 결과 문장에 반복하지",
    ):
        assert text in prompt
