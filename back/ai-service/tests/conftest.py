import base64
import json
from io import BytesIO
from uuid import uuid4

import pytest
from pypdf import PdfWriter
from pypdf.generic import (
    ArrayObject,
    DecodedStreamObject,
    DictionaryObject,
    NameObject,
    NumberObject,
)

from app.providers.base import ProviderResult
from app.schemas.jev import LEGACY_DOCUMENT_KINDS, Findings, JevRequest


@pytest.fixture
def catalog_entry():
    def entry(provider="gemini", model="private-test-model", **changes):
        return {
            "provider": provider,
            "model": model,
            "enabled": True,
            "capabilities": ["pdf_input", "structured_output", "token_usage"],
            "max_pdf_documents": 3,
            "max_output_tokens": 16000,
            "source": "synthetic test declaration; not a real model",
            **changes,
        }

    return entry


@pytest.fixture
def catalog_path(tmp_path):
    def write(entries):
        path = tmp_path / "model-catalog.json"
        path.write_text(json.dumps({"schema_version": 1, "models": entries}), encoding="utf-8")
        return path

    return write


@pytest.fixture(autouse=True)
def isolated_settings(monkeypatch, tmp_path):
    from app.api.jev import service
    from app.core.config import settings
    from app.core.usage import usage_guard
    from app.main import app

    # Local-mode tests must never read a developer's real .env file.
    monkeypatch.chdir(tmp_path)
    for name in (
        "GOOGLE_API_KEY",
        "OPENAI_API_KEY",
        "OPENAI_MODEL",
        "OPENAI_REASONING_EFFORT",
        "OPENAI_VERBOSITY",
        "LANGSMITH_API_KEY",
        "JEV_SERVICE_TOKEN",
        "JEV_SECRETS_DIR",
        "GEMINI_MODEL",
        "JEV_MODEL_CATALOG_PATH",
        "JEV_ROUTING_POLICY",
        "JEV_MODEL_PRIORITY",
        "JEV_MAX_ATTEMPTS",
        "JEV_RETRY_ATTEMPTS",
        "JEV_FALLBACK_ENABLED",
        "JEV_ATTEMPT_TIMEOUT_SECONDS",
        "JEV_EXECUTION_BUDGET_SECONDS",
        "JEV_RETRY_BASE_DELAY_SECONDS",
        "JEV_CIRCUIT_ENABLED",
        "JEV_CIRCUIT_FAILURE_THRESHOLD",
        "JEV_CIRCUIT_COOLDOWN_SECONDS",
        "JEV_LIMITS_ENABLED",
        "JEV_REQUESTS_PER_MINUTE",
        "JEV_ATTEMPTS_PER_MINUTE",
        "JEV_MAX_CONCURRENT_REQUESTS",
        "JEV_MAX_CONCURRENT_CALLS",
        "JEV_COST_GUARD_ENABLED",
        "JEV_REQUEST_BUDGET_USD",
        "JEV_DAILY_BUDGET_USD",
        "JEV_PRICING_MAX_AGE_DAYS",
        "LANGSMITH_ENDPOINT",
        "PYDANTIC_SETTINGS_DEBUG",
    ):
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setenv("APP_ENV", "test")
    monkeypatch.setenv("JEV_PROVIDER", "stub")
    monkeypatch.setenv("LANGSMITH_ENABLED", "false")
    settings.cache_clear()
    service.cache_clear()
    usage_guard.cache_clear()
    yield
    app.dependency_overrides.clear()
    settings.cache_clear()
    service.cache_clear()
    usage_guard.cache_clear()


def text_pdf(*pages: list[str]) -> bytes:
    """PDF whose text layer contains the given lines (no font program; extraction only)."""
    writer = PdfWriter()
    cmap = DecodedStreamObject()
    ranges = "".join(f"<{h:02X}00> <{h:02X}FF> <{h:02X}00>\n" for h in range(256))
    cmap.set_data(
        (
            "/CIDInit /ProcSet findresource begin 12 dict begin begincmap\n"
            "1 begincodespacerange <0000> <FFFF> endcodespacerange\n"
            f"256 beginbfrange\n{ranges}endbfrange\nendcmap end end"
        ).encode()
    )
    descendant = DictionaryObject(
        {
            NameObject("/Type"): NameObject("/Font"),
            NameObject("/Subtype"): NameObject("/CIDFontType2"),
            NameObject("/BaseFont"): NameObject("/TestText"),
            NameObject("/DW"): NumberObject(1000),
        }
    )
    font = writer._add_object(
        DictionaryObject(
            {
                NameObject("/Type"): NameObject("/Font"),
                NameObject("/Subtype"): NameObject("/Type0"),
                NameObject("/BaseFont"): NameObject("/TestText"),
                NameObject("/Encoding"): NameObject("/Identity-H"),
                NameObject("/DescendantFonts"): ArrayObject([writer._add_object(descendant)]),
                NameObject("/ToUnicode"): writer._add_object(cmap),
            }
        )
    )
    for lines in pages:
        page = writer.add_blank_page(width=595, height=842)
        content = DecodedStreamObject()
        content.set_data(
            "".join(
                f"BT /F1 10 Tf 40 {800 - 20 * i} Td <{line.encode('utf-16-be').hex()}> Tj ET\n"
                for i, line in enumerate(lines)
            ).encode()
        )
        page[NameObject("/Contents")] = writer._add_object(content)
        page[NameObject("/Resources")] = DictionaryObject(
            {NameObject("/Font"): DictionaryObject({NameObject("/F1"): font})}
        )
    stream = BytesIO()
    writer.write(stream)
    return stream.getvalue()


# Synthetic text-layer forms with every required field: complete bundles reach the model.
COMMON_LINES = [
    "환자 성명: 테스트-김하늘",
    "주민등록번호: 900101-1234567",
    "의료기관: 가상 테스트의원",
    "진료일: 2026-09-01",
]
FORM_PAGES = {
    "DIAGNOSIS": ["진단서", *COMMON_LINES, "진단명: 가상 질환", "질병분류기호: J20.9"],
    "TREATMENT_CONFIRMATION": ["진료확인서", *COMMON_LINES, "진단명: 가상 질환"],
    "ITEMIZED_BILL": [
        "진료비 세부산정내역서",
        *COMMON_LINES,
        "진료비 총액 50,000 원",
        "환자부담 총액 12,300 원",
    ],
    "INITIAL_VISIT_RECORD": [
        "초진기록지",
        *COMMON_LINES,
        "주호소: 가상 증상",
        "현병력: 3일 전 시작",
        "진단명: 가상 질환",
        "질병분류기호: J20.9",
    ],
    "MEDICAL_RECEIPT": [
        "진료비 계산서·영수증",
        *COMMON_LINES,
        "진료비 총액 50,000 원",
        "환자부담 총액 12,300 원",
        "납부액 12,300 원",
    ],
}


@pytest.fixture
def request_data():
    return {
        "request_id": str(uuid4()),
        "documents": [
            {
                "document_id": str(uuid4()),
                "kind": kind.value,
                "content_base64": base64.b64encode(text_pdf(FORM_PAGES[kind.value])).decode(),
            }
            for kind in LEGACY_DOCUMENT_KINDS
        ],
    }


@pytest.fixture
def sample_request(request_data):
    return JevRequest.model_validate(request_data)


class FakeProvider:
    name = "gemini"
    model = "fake-test-model"

    def analyze(self, request):
        return ProviderResult(
            Findings(
                items=[
                    {
                        "item": "진료일자",
                        "judgment": "SUITABLE",
                        "evidence": [
                            {
                                "document_id": request.documents[0].document_id,
                                "page": 1,
                                "description": "날짜 항목 확인",
                            }
                        ],
                        "confidence": 0.8,
                        "verification_reason": "날짜 판독 가능",
                        "supplement": "",
                    }
                ],
                risk_signals=[],
                summary="합성 요약: 진료일자 항목을 확인했습니다.",
            ),
            10,
            20,
        )
