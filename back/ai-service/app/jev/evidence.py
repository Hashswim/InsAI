"""Collect model-proposed citations against request-local PDF references only.

No OCR, network access, model invocation, or claim of factual verification.
"""

from dataclasses import replace

from app.jev.structured_output import validate_findings
from app.providers.base import ProviderResult
from app.schemas.jev import Item, JevRequest, RiskSignal, supported_document_kinds


class EvidenceChain:
    def run(self, request: JevRequest, draft: ProviderResult) -> ProviderResult:
        if (
            not isinstance(request, JevRequest)
            or len(request.documents) != 3
            or len({document.document_id for document in request.documents}) != 3
            or not supported_document_kinds({document.kind for document in request.documents})
        ):
            raise ValueError("Invalid E-stage document scope")
        if not isinstance(draft, ProviderResult):
            raise ValueError("Invalid E-stage draft")
        findings = validate_findings(draft.findings)
        try:
            # Derive pages from the actual accepted PDFs, never from model metadata.
            pages = {document.document_id: document.page_count() for document in request.documents}
            if any(type(count) is not int or not 1 <= count <= 30 for count in pages.values()):
                raise ValueError()
        except Exception:
            # Parser exceptions may contain document data or implementation details.
            raise ValueError("E-stage PDF reference index is unavailable") from None
        # Risk signals are held to the same citation rules as judgments.
        cited: list[Item | RiskSignal] = [*findings.items, *findings.risk_signals]
        for item in cited:
            seen: set[tuple[str, int, str]] = set()
            for evidence in item.evidence:
                if evidence.document_id not in pages or evidence.page > pages[evidence.document_id]:
                    raise ValueError("Evidence refers to an unknown document or page")
                key = (
                    str(evidence.document_id),
                    evidence.page,
                    " ".join(evidence.description.split()),
                )
                if key in seen:
                    # Never inflate support or silently select between duplicates.
                    raise ValueError("Duplicate E-stage evidence")
                seen.add(key)
        return replace(draft, findings=findings)
