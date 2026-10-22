"""Keyword extraction and code checks over PDF text layers; the only text a model may receive.

No OCR: scanned/image-only pages have no text layer and are reported as such.
Presence of a label and a value is checked, never the meaning or accuracy of the value.
"""

import json
import re
from dataclasses import dataclass, field
from datetime import date
from io import BytesIO
from uuid import UUID

from pypdf import PdfReader

from app.schemas.jev import (
    OUTPATIENT_DOCUMENT_KINDS,
    DocumentKind,
    Evidence,
    Findings,
    Item,
    JevRequest,
)

FORM_VERSION = "jev-text-form-v1"
RULES_MODEL = "jev-rules-v1"  # Reported when gaps go straight back to the claimant.
MAX_PAGE_CHARS = 20_000
MAX_VALUE_CHARS = 300
Specs = dict[str, tuple[str, tuple[str, ...]]]

KIND_NAMES = {
    DocumentKind.DIAGNOSIS: "진단서",
    DocumentKind.TREATMENT_CONFIRMATION: "진료확인서",
    DocumentKind.ITEMIZED_BILL: "진료비 세부내역서",
    DocumentKind.INITIAL_VISIT_RECORD: "초진기록지",
    DocumentKind.MEDICAL_RECEIPT: "진료비 영수증",
}
TITLES = {
    DocumentKind.DIAGNOSIS: ("진단서",),
    DocumentKind.TREATMENT_CONFIRMATION: ("진료확인서", "통원확인서", "입퇴원확인서", "입원확인서"),
    DocumentKind.ITEMIZED_BILL: ("세부내역서", "세부산정내역"),
    DocumentKind.INITIAL_VISIT_RECORD: ("초진기록",),
    DocumentKind.MEDICAL_RECEIPT: ("영수증",),
}
# Field name -> (Korean display name, labels). Earlier labels win; spaces are optional.
DIAGNOSIS_NAME = ("진단명", ("상병명", "병명", "진단명"))
DISEASE_CODE = ("질병분류기호", ("질병분류기호", "상병코드", "분류기호"))
GROSS = ("진료비 총액", ("진료비총액", "총진료비"))
PATIENT_DUE = ("환자부담 총액", ("환자부담총액", "본인부담총액"))
AMOUNT_FIELDS = (
    "gross",
    "patient_due",
    "covered_patient",
    "insurer",
    "full_patient",
    "noncovered",
    "paid",
)
AMOUNTS: Specs = {
    "covered_patient": ("급여 본인부담금", ("급여본인부담금", "급여본인부담")),
    "insurer": ("공단부담금", ("공단부담금", "공단부담")),
    "full_patient": ("전액본인부담금", ("전액본인부담금", "전액본인부담")),
    "noncovered": ("비급여", ("비급여합계", "비급여")),
}
REQUIRED: Specs = {
    "patient": ("환자 성명", ("환자성명", "수진자성명", "환자명", "성명")),
    "visit_date": ("진료일", ("진료일자", "진료일", "진료기간", "내원일")),
    "issuer": ("의료기관", ("의료기관명", "의료기관", "요양기관명", "요양기관", "병원명")),
}
OPTIONAL: Specs = {
    # Only the six-digit front part is kept; see _clean().
    "resident_id_front": ("주민등록번호 앞자리", ("주민등록번호", "주민번호")),
    "issue_date": ("발급일", ("발급일자", "발급일", "발행일")),
    "department": ("진료과", ("진료과",)),
}
# A resident registration number anywhere in a value; the back part is never retained.
RESIDENT_ID = re.compile(r"(?<![0-9])([0-9]{6})\s*-\s*[0-9*]{1,7}")
KIND_REQUIRED: dict[DocumentKind, Specs] = {
    DocumentKind.DIAGNOSIS: {"diagnosis": DIAGNOSIS_NAME, "disease_code": DISEASE_CODE},
    DocumentKind.TREATMENT_CONFIRMATION: {"diagnosis": DIAGNOSIS_NAME},
    DocumentKind.ITEMIZED_BILL: {"gross": GROSS, "patient_due": PATIENT_DUE},
    DocumentKind.INITIAL_VISIT_RECORD: {
        "chief_complaint": ("주호소", ("주호소", "주증상")),
        "present_illness": ("현병력", ("현병력",)),
        "diagnosis": DIAGNOSIS_NAME,
        "disease_code": DISEASE_CODE,
    },
    DocumentKind.MEDICAL_RECEIPT: {
        "gross": GROSS,
        "patient_due": PATIENT_DUE,
        "paid": ("납부액", ("납부한금액", "납부액", "수납금액")),
    },
}
KIND_OPTIONAL: dict[DocumentKind, Specs] = {
    DocumentKind.DIAGNOSIS: {"onset_date": ("발병일", ("발병일",))},
    DocumentKind.TREATMENT_CONFIRMATION: {"disease_code": DISEASE_CODE},
    DocumentKind.ITEMIZED_BILL: AMOUNTS,
    DocumentKind.INITIAL_VISIT_RECORD: {
        "onset_date": ("증상 시작일", ("증상시작일", "발병일")),
        "history": ("과거력", ("과거력",)),
        "diagnosis_status": ("진단 상태", ("진단상태",)),
        "treatment_plan": ("치료 계획", ("처치및검사계획", "치료계획")),
    },
    DocumentKind.MEDICAL_RECEIPT: {
        **AMOUNTS,
        "prepaid": ("기납부액", ("기납부액", "기납부")),
        "unpaid": ("미수금", ("미수금",)),
    },
}


def _pattern(label: str) -> re.Pattern[str]:
    # A following Hangul syllable means a different label, e.g. 진료일 vs 진료일수.
    chars = r"\s*".join(map(re.escape, label))
    return re.compile(rf"^\s*{chars}(?![가-힣])\s*[:：]?\s*(.*)$")


PATTERNS = {
    label: _pattern(label)
    for specs in (REQUIRED, OPTIONAL, *KIND_REQUIRED.values(), *KIND_OPTIONAL.values())
    for _, labels in specs.values()
    for label in labels
}


@dataclass(frozen=True)
class FieldValue:
    value: str = field(repr=False)
    page: int


@dataclass(frozen=True)
class DocumentText:
    document_id: UUID
    kind: DocumentKind
    pages: int
    text_layer: bool
    title_page: int | None
    fields: dict[str, FieldValue] = field(repr=False)

    @property
    def required(self) -> Specs:
        return {**REQUIRED, **KIND_REQUIRED[self.kind]}

    @property
    def missing(self) -> tuple[str, ...]:
        return tuple(
            name for name in self.required if not getattr(self.fields.get(name), "value", "")
        )


def _compact(text: str) -> str:
    return "".join(text.split())


def _digits(text: str) -> str:
    return re.sub(r"[^0-9]", "", text)


def _clean(name: str, text: str) -> str:
    if name == "resident_id_front":
        match = re.match(r"([0-9]{6})(?![0-9])", text)
        return match.group(1) if match else ""
    return RESIDENT_ID.sub(r"\1-*******", text)[:MAX_VALUE_CHARS]


def _amount(text: str) -> int | None:
    match = re.fullmatch(r"([0-9][0-9,]*)\s*원?", text.strip())
    return int(match.group(1).replace(",", "")) if match else None


def read_document(document_id: UUID, kind: DocumentKind, raw: bytes) -> DocumentText:
    reader = PdfReader(BytesIO(raw))
    pages = len(reader.pages)
    specs = {**REQUIRED, **OPTIONAL, **KIND_REQUIRED[kind], **KIND_OPTIONAL[kind]}
    found: dict[str, FieldValue] = {}
    title_page = None
    text_layer = False
    for number, page in enumerate(reader.pages, start=1):
        try:
            text = (page.extract_text() or "")[:MAX_PAGE_CHARS]
        except Exception:
            # Unsupported fonts/streams behave like a page without a text layer.
            continue
        lines = [line for line in text.splitlines() if line.strip()]
        text_layer = text_layer or bool(lines)
        if title_page is None and any(
            title in _compact(line) for line in lines[:5] for title in TITLES[kind]
        ):
            title_page = number
        for line in lines:
            for name, (_, labels) in specs.items():
                if getattr(found.get(name), "value", ""):
                    continue
                for label in labels:
                    if match := PATTERNS[label].match(line):
                        found[name] = FieldValue(_clean(name, match.group(1).strip()), number)
                        break
    return DocumentText(document_id, kind, pages, text_layer, title_page, found)


def _date(text: str) -> date | None:
    """First date in a value (a period yields its start); None when not a clear date."""
    match = re.search(r"(\d{4})\s*[-./년]\s*(\d{1,2})\s*[-./월]\s*(\d{1,2})", text)
    try:
        return date(*map(int, match.groups())) if match else None
    except ValueError:
        return None


def _document_flags(d: DocumentText) -> list[str]:
    """Code-detectable inconsistencies inside one document; hints for the model, not findings."""
    values = {name: field.value for name, field in d.fields.items()}
    flags = []
    visit = _date(values.get("visit_date", ""))
    onset = _date(values.get("onset_date", ""))
    issue = _date(values.get("issue_date", ""))
    if visit and onset and onset > visit:
        flags.append("ONSET_AFTER_VISIT")
    if visit and issue and issue < visit:
        flags.append("ISSUE_BEFORE_VISIT")
    amount = {name: _amount(values[name]) for name in AMOUNT_FIELDS if name in values}
    due, gross = amount.get("patient_due"), amount.get("gross")
    if due is not None and gross is not None and due > gross:
        flags.append("PATIENT_DUE_EXCEEDS_GROSS")
    parts = [amount.get("covered_patient"), amount.get("noncovered")]
    if due is not None and None not in parts:
        if sum(parts) + (amount.get("full_patient") or 0) != due:  # type: ignore[arg-type]
            flags.append("PATIENT_DUE_BREAKDOWN_MISMATCH")
    paid = amount.get("paid")
    if due is not None and paid is not None and paid > due:
        flags.append("PAID_EXCEEDS_DUE")
    return [f"{d.kind.value}:{flag}" for flag in flags]


@dataclass(frozen=True)
class Precheck:
    documents: tuple[DocumentText, ...]
    outpatient: bool

    @property
    def flags(self) -> tuple[str, ...]:
        """Cross-document mismatches and in-document anomalies the model should explain."""
        flags = []
        for name, flag, normalize in (
            ("patient", "PATIENT_MISMATCH", _compact),
            ("visit_date", "VISIT_DATE_MISMATCH", _digits),
            ("resident_id_front", "RESIDENT_ID_FRONT_MISMATCH", _digits),
        ):
            values = {normalize(d.fields[name].value) for d in self.documents if name in d.fields}
            if len(values) > 1:
                flags.append(flag)
        if self.outpatient:
            dues = [
                _amount(d.fields["patient_due"].value) if "patient_due" in d.fields else None
                for d in self.documents
                if d.kind in (DocumentKind.ITEMIZED_BILL, DocumentKind.MEDICAL_RECEIPT)
            ]
            if None in dues:
                flags.append("PATIENT_DUE_UNREADABLE")
            elif len(set(dues)) > 1:
                flags.append("PATIENT_DUE_MISMATCH")
        for d in self.documents:
            flags.extend(_document_flags(d))
        return tuple(flags)

    @property
    def complete(self) -> bool:
        """Every document readable, recognized and filled in: only then is it worth a model call."""
        return all(
            d.text_layer and d.title_page is not None and not d.missing for d in self.documents
        )

    def form(self) -> str:
        """Extracted fields only. Patient names become bundle-local labels."""
        aliases: dict[str, str] = {}
        documents = []
        for d in self.documents:
            values = {}
            for name, found in d.fields.items():
                text = found.value
                if name == "patient" and text:
                    text = aliases.setdefault(_compact(text), f"환자-{chr(65 + len(aliases))}")
                values[name] = {"value": text, "page": found.page}
            documents.append(
                {
                    "document_id": str(d.document_id),
                    "kind": d.kind.value,
                    "pages": d.pages,
                    "text_layer": d.text_layer,
                    "title_found": d.title_page is not None,
                    "fields": values,
                    "missing": list(d.missing) if d.text_layer else [],
                }
            )
        value = {
            "form_version": FORM_VERSION,
            "bundle": "OUTPATIENT_THREE_DOCS" if self.outpatient else "LEGACY_MEDICAL_THREE_DOCS",
            "regulations_status": "NOT_REGISTERED",
            "documents": documents,
            "server_flags": list(self.flags),
        }
        return "JEV_TEXT_FORM=" + json.dumps(value, ensure_ascii=False, separators=(",", ":"))

    def claimant_findings(self) -> Findings:
        """Obvious gaps told straight back to the claimant, without a model call."""
        if self.complete:
            raise ValueError("A complete bundle goes to the model for the adjuster")
        items = []
        for d in self.documents:
            name = KIND_NAMES[d.kind]
            if not d.text_layer:
                items.append(
                    Item(
                        item=f"{name} 판독 가능 여부",
                        judgment="UNVERIFIABLE",
                        evidence=[],
                        confidence=0,
                        verification_reason="스캔·이미지 PDF라 글자를 읽을 수 없음.",
                        supplement="전자 발급본(텍스트가 포함된 PDF)으로 다시 제출해 주세요.",
                    )
                )
                continue
            page = d.title_page or 1
            if d.title_page is None:
                items.append(
                    _gap(
                        d,
                        f"{name} 서류 종류",
                        page,
                        f"문서 앞부분에서 '{name}' 제목을 찾지 못함",
                        f"{name}가 맞는지 확인하고 올바른 서류로 다시 제출해 주세요.",
                    )
                )
            for missing in d.missing:
                display = d.required[missing][0]
                items.append(
                    _gap(
                        d,
                        f"{name} {display}",
                        d.fields[missing].page if missing in d.fields else page,
                        f"'{display}' 항목의 값을 찾지 못함",
                        f"{display}이(가) 기재된 {name}로 다시 제출해 주세요.",
                    )
                )
            if d.title_page is not None and not d.missing:
                items.append(
                    Item(
                        item=f"{name} 필수 항목",
                        judgment="SUITABLE",
                        evidence=[
                            Evidence(
                                document_id=d.document_id,
                                page=page,
                                description="제목과 필수 항목의 기재를 확인함",
                            )
                        ],
                        confidence=1,
                        verification_reason="규칙 점검: 필수 항목의 라벨과 값을 모두 찾음.",
                        supplement="",
                    )
                )
        gaps = [it.item for it in items if it.judgment != "SUITABLE"]
        return Findings(
            items=items,
            risk_signals=[],
            summary=(
                f"보완이 필요한 항목이 {len(gaps)}건 있습니다: {', '.join(gaps)}. "
                "보완한 서류로 다시 제출하시면 담당 손해사정사에게 전달됩니다."
            ),
        )


def _gap(d: DocumentText, item: str, page: int, description: str, supplement: str) -> Item:
    return Item(
        item=item,
        judgment="NEEDS_SUPPLEMENT",
        evidence=[Evidence(document_id=d.document_id, page=page, description=description)],
        confidence=1,
        verification_reason=(
            "규칙 점검: PDF 텍스트에서 확인하지 못함. 표 형식 등으로 읽지 못했을 수도 있음."
        ),
        supplement=supplement,
    )


def precheck(request: JevRequest) -> Precheck:
    documents = tuple(
        read_document(d.document_id, d.kind, d.pdf_bytes()) for d in request.documents
    )
    kinds = {d.kind for d in documents}
    return Precheck(documents, kinds == set(OUTPATIENT_DOCUMENT_KINDS))
