import base64
from io import BytesIO
from uuid import uuid4

import pytest
from pypdf import PdfWriter

from app.providers.base import ProviderResult
from app.schemas.jev import DocumentKind, Findings, JevRequest


@pytest.fixture(autouse=True)
def isolated_settings(monkeypatch):
    from app.api.jev import service
    from app.core.config import settings
    from app.main import app

    for name in (
        "GOOGLE_API_KEY",
        "LANGSMITH_API_KEY",
        "JEV_SERVICE_TOKEN",
        "JEV_SECRETS_DIR",
        "GEMINI_MODEL",
        "LANGSMITH_ENDPOINT",
        "PYDANTIC_SETTINGS_DEBUG",
    ):
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setenv("APP_ENV", "test")
    monkeypatch.setenv("JEV_PROVIDER", "stub")
    monkeypatch.setenv("LANGSMITH_ENABLED", "false")
    settings.cache_clear()
    service.cache_clear()
    yield
    app.dependency_overrides.clear()
    settings.cache_clear()
    service.cache_clear()


@pytest.fixture
def request_data():
    writer = PdfWriter()
    writer.add_blank_page(width=300, height=300)
    stream = BytesIO()
    writer.write(stream)
    encoded = base64.b64encode(stream.getvalue()).decode()
    return {
        "request_id": str(uuid4()),
        "documents": [
            {"document_id": str(uuid4()), "kind": kind.value, "content_base64": encoded}
            for kind in DocumentKind
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
                ]
            ),
            10,
            20,
        )
