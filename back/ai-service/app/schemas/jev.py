import base64
from enum import Enum
from io import BytesIO
from typing import Self
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, model_validator
from pypdf import PdfReader


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class DocumentKind(str, Enum):
    DIAGNOSIS = "DIAGNOSIS"
    TREATMENT_CONFIRMATION = "TREATMENT_CONFIRMATION"
    ITEMIZED_BILL = "ITEMIZED_BILL"


class PdfDocument(StrictModel):
    document_id: UUID
    kind: DocumentKind
    content_base64: str = Field(min_length=1, max_length=14_000_000)

    def pdf_bytes(self) -> bytes:
        return base64.b64decode(self.content_base64, validate=True)

    def page_count(self) -> int:
        return len(PdfReader(BytesIO(self.pdf_bytes())).pages)

    @model_validator(mode="after")
    def valid_pdf(self) -> Self:
        try:
            raw = self.pdf_bytes()
            if len(raw) > 10 * 1024 * 1024 or not raw.startswith(b"%PDF-"):
                raise ValueError()
            reader = PdfReader(BytesIO(raw))
            if reader.is_encrypted or not 1 <= len(reader.pages) <= 30:
                raise ValueError()
        except Exception:
            raise ValueError("Invalid, encrypted, oversized, or over-30-page PDF") from None
        return self


class JevRequest(StrictModel):
    request_id: UUID
    documents: list[PdfDocument] = Field(min_length=3, max_length=3)

    @model_validator(mode="after")
    def unique_documents(self) -> Self:
        if len({d.document_id for d in self.documents}) != 3:
            raise ValueError("Duplicate document IDs")
        if {d.kind for d in self.documents} != set(DocumentKind):
            raise ValueError("Exactly one document of each kind is required")
        return self


class Evidence(StrictModel):
    document_id: UUID
    page: int = Field(ge=1)
    description: str = Field(min_length=1, max_length=2000)


class Item(StrictModel):
    item: str = Field(min_length=1, max_length=200)
    judgment: str = Field(pattern="^(SUITABLE|NEEDS_SUPPLEMENT|UNVERIFIABLE)$")
    evidence: list[Evidence] = Field(max_length=20)
    confidence: float = Field(ge=0, le=1)
    verification_reason: str = Field(min_length=1, max_length=2000)
    supplement: str = Field(max_length=2000)

    @model_validator(mode="after")
    def grounded(self) -> Self:
        if self.judgment != "UNVERIFIABLE" and not self.evidence:
            raise ValueError("A judgment needs evidence")
        if self.judgment == "UNVERIFIABLE":
            self.confidence = 0
        if self.judgment == "NEEDS_SUPPLEMENT" and not self.supplement.strip():
            raise ValueError("Supplement instructions required")
        return self


class Findings(StrictModel):
    items: list[Item] = Field(min_length=1, max_length=100)


class JevResponse(StrictModel):
    request_id: UUID
    submission_status: str = "ON_HOLD"
    regulations_status: str = "NOT_REGISTERED"
    reason: str = "규정 미등록: 최종 제출 가능 여부는 판정 보류입니다."
    items: list[Item]
    provider: str
    model: str
    prompt_version: str = "jev-v1"
    trace_id: UUID | None
    trace_status: str
    input_tokens: int = 0
    output_tokens: int = 0
