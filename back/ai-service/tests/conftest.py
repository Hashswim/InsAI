import base64
from io import BytesIO
from uuid import uuid4
import pytest
from pypdf import PdfWriter
from app.schemas.jev import JevRequest, DocumentKind, Findings
from app.providers.base import ProviderResult


@pytest.fixture
def request_data():
    writer = PdfWriter()
    writer.add_blank_page(width=300, height=300)
    stream = BytesIO()
    writer.write(stream)
    encoded = base64.b64encode(stream.getvalue()).decode()
    return {"request_id": str(uuid4()), "documents": [
        {"document_id": str(uuid4()), "kind": kind.value, "content_base64": encoded}
        for kind in DocumentKind
    ]}


@pytest.fixture
def sample_request(request_data):
    return JevRequest.model_validate(request_data)


class FakeProvider:
    name = "gemini"
    model = "fake-test-model"

    def analyze(self, request):
        return ProviderResult(Findings(items=[{
            "item": "진료일자", "judgment": "SUITABLE",
            "evidence": [{"document_id": request.documents[0].document_id,
                          "page": 1, "description": "날짜 항목 확인"}],
            "confidence": 0.8, "verification_reason": "날짜 판독 가능", "supplement": ""
        }]), 10, 20)
