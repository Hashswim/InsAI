import base64
from enum import Enum
from io import BytesIO
from typing import Literal, Self
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from pypdf import PdfReader

from app.core.pdf_safety import require_passive_pdf


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


def valid_text(value: str, *, allow_blank: bool = False) -> str:
    if not allow_blank and not value.strip():
        raise ValueError("Nonblank text required")
    try:
        value.encode("utf-8")
    except UnicodeError:
        raise ValueError("Valid Unicode text required") from None
    return value


class DocumentKind(str, Enum):
    DIAGNOSIS = "DIAGNOSIS"
    TREATMENT_CONFIRMATION = "TREATMENT_CONFIRMATION"
    ITEMIZED_BILL = "ITEMIZED_BILL"
    INITIAL_VISIT_RECORD = "INITIAL_VISIT_RECORD"
    MEDICAL_RECEIPT = "MEDICAL_RECEIPT"


LEGACY_DOCUMENT_KINDS = (
    DocumentKind.DIAGNOSIS,
    DocumentKind.TREATMENT_CONFIRMATION,
    DocumentKind.ITEMIZED_BILL,
)
OUTPATIENT_DOCUMENT_KINDS = (
    DocumentKind.ITEMIZED_BILL,
    DocumentKind.INITIAL_VISIT_RECORD,
    DocumentKind.MEDICAL_RECEIPT,
)


def supported_document_kinds(kinds: set[DocumentKind]) -> bool:
    return kinds in (set(LEGACY_DOCUMENT_KINDS), set(OUTPATIENT_DOCUMENT_KINDS))


class PdfDocument(StrictModel):
    document_id: UUID
    kind: DocumentKind
    content_base64: str = Field(
        min_length=1,
        max_length=14_000_000,
        description="Raw PDF bytes as base64, without a data URL prefix",
    )

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
            require_passive_pdf(reader)
        except Exception:
            raise ValueError("Invalid or unsupported PDF") from None
        return self


class JevRequest(StrictModel):
    request_id: UUID = Field(description="Correlation ID; must match X-Request-ID when supplied")
    documents: list[PdfDocument] = Field(min_length=3, max_length=3)

    @model_validator(mode="after")
    def unique_documents(self) -> Self:
        if len({d.document_id for d in self.documents}) != 3:
            raise ValueError("Duplicate document IDs")
        if not supported_document_kinds({d.kind for d in self.documents}):
            raise ValueError("Exactly one document of each kind in a supported bundle is required")
        return self


class Evidence(StrictModel):
    document_id: UUID
    page: int = Field(ge=1, strict=True)
    description: str = Field(min_length=1, max_length=2000)

    @field_validator("description")
    @classmethod
    def nonblank_description(cls, value: str) -> str:
        return valid_text(value)


class Item(StrictModel):
    item: str = Field(min_length=1, max_length=200)
    judgment: Literal["SUITABLE", "NEEDS_SUPPLEMENT", "UNVERIFIABLE"]
    evidence: list[Evidence] = Field(max_length=20, strict=True)
    confidence: float = Field(ge=0, le=1, strict=True, allow_inf_nan=False)
    verification_reason: str = Field(min_length=1, max_length=2000)
    supplement: str = Field(max_length=2000)

    @field_validator("item", "verification_reason")
    @classmethod
    def nonblank_text(cls, value: str) -> str:
        return valid_text(value)

    @field_validator("supplement")
    @classmethod
    def unicode_supplement(cls, value: str) -> str:
        return valid_text(value, allow_blank=True)

    @model_validator(mode="after")
    def grounded(self) -> Self:
        if self.judgment != "UNVERIFIABLE" and not self.evidence:
            raise ValueError("A judgment needs evidence")
        if self.judgment == "UNVERIFIABLE":
            self.confidence = 0
        if self.judgment == "NEEDS_SUPPLEMENT" and not self.supplement.strip():
            raise ValueError("Supplement instructions required")
        return self


class RiskSignal(StrictModel):
    """A point the adjuster should verify (possible false/inflated claim), never a fraud finding."""

    signal: str = Field(min_length=1, max_length=200)
    level: Literal["LOW", "MEDIUM", "HIGH"]
    reason: str = Field(min_length=1, max_length=2000)
    evidence: list[Evidence] = Field(min_length=1, max_length=20, strict=True)

    @field_validator("signal", "reason")
    @classmethod
    def nonblank_text(cls, value: str) -> str:
        return valid_text(value)


class PolicyRef(StrictModel):
    """A standard-terms clause retrieved by RAG. Text fields come from the corpus, not the model."""

    clause_id: str = Field(pattern=r"^[a-z0-9]+:[a-z0-9_]+:[0-9]+$", max_length=64)
    product: str = Field(min_length=1, max_length=60)
    article: str = Field(min_length=1, max_length=40)
    title: str = Field(min_length=1, max_length=120)
    excerpt: str = Field(min_length=1, max_length=400)
    reason: str = Field(min_length=1, max_length=500)

    @field_validator("reason")
    @classmethod
    def nonblank_reason(cls, value: str) -> str:
        return valid_text(value)


class Findings(StrictModel):
    # Field order is the model's generation order: details first, summary of them last.
    items: list[Item] = Field(min_length=1, max_length=100, strict=True)
    risk_signals: list[RiskSignal] = Field(max_length=20, strict=True)
    summary: str = Field(min_length=1, max_length=2000, description="손해사정사용 요약")

    @field_validator("summary")
    @classmethod
    def nonblank_summary(cls, value: str) -> str:
        return valid_text(value)


class VerificationSummary(StrictModel):
    """Server-owned scope disclosure, not a model claim of factual accuracy."""

    model_config = ConfigDict(extra="forbid", frozen=True, revalidate_instances="always")
    method: Literal["STRUCTURAL"]
    factual_consistency: Literal["NOT_VERIFIED"]
    confidence_basis: Literal["MODEL_SELF_ASSESSMENT", "OFFLINE_STUB", "RULE_PRESENCE_CHECK"]
    human_review: Literal["REQUIRED"]


class JevResponse(StrictModel):
    request_id: UUID
    submission_status: Literal["ON_HOLD"] = "ON_HOLD"
    regulations_status: Literal["NOT_REGISTERED"] = "NOT_REGISTERED"
    reason: str = "규정 미등록: 최종 제출 가능 여부는 판정 보류입니다."
    items: list[Item] = Field(min_length=1, max_length=100)
    risk_signals: list[RiskSignal] = Field(max_length=20)
    summary: str = Field(min_length=1, max_length=2000)
    policy_refs: list[PolicyRef] = Field(default_factory=list, max_length=10)
    provider: Literal["gemini", "stub", "openai", "rules"]
    model: str = Field(min_length=1, max_length=200)
    prompt_version: str = Field(min_length=1, max_length=100)
    prompt_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    chain_version: str = Field(min_length=1, max_length=100)
    verification: VerificationSummary
    trace_id: UUID | None
    trace_status: Literal["RECORDED", "DISABLED", "FAILED"]
    input_tokens: int = Field(default=0, ge=0)
    output_tokens: int = Field(default=0, ge=0)

    @field_validator("summary")
    @classmethod
    def nonblank_summary(cls, value: str) -> str:
        return valid_text(value)

    @model_validator(mode="after")
    def trace_consistency(self) -> Self:
        if (self.trace_status == "RECORDED") != (self.trace_id is not None):
            raise ValueError("Trace ID must be present exactly when trace is recorded")
        basis = {"stub": "OFFLINE_STUB", "rules": "RULE_PRESENCE_CHECK"}.get(
            self.provider, "MODEL_SELF_ASSESSMENT"
        )
        if self.verification.confidence_basis != basis:
            raise ValueError("Verification basis does not match execution mode")
        if self.provider == "stub" and any(
            item.judgment != "UNVERIFIABLE" or item.evidence for item in self.items
        ):
            raise ValueError("Offline stub cannot make grounded recommendations")
        if self.provider in {"stub", "rules"} and (self.risk_signals or self.policy_refs):
            raise ValueError("Only a model analysis can raise risk signals or cite clauses")
        if self.provider == "rules" and (self.input_tokens or self.output_tokens):
            raise ValueError("Rule-only results cannot report model token usage")
        return self
