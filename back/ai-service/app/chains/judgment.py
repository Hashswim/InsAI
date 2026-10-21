"""J-stage boundary: one model draft, never a final submission decision."""

from langsmith import tracing_context

from app.chains.structured_output import validate_findings
from app.providers.base import Provider, ProviderDocument, ProviderRequest, ProviderResult
from app.schemas.jev import DocumentKind


class JudgmentChain:
    """Use the existing structured provider port without extra model calls.

    The current draft envelope includes model-proposed E/V fields. Downstream
    checks still own document/page validity; this is not independent E/V review.
    """

    def __init__(self, provider: Provider) -> None:
        self.provider = provider

    def run(self, request: ProviderRequest) -> ProviderResult:
        if (
            not isinstance(request, ProviderRequest)
            or len(request.documents) != 3
            or not all(isinstance(document, ProviderDocument) for document in request.documents)
            or len({document.document_id for document in request.documents}) != 3
            or {document.kind for document in request.documents} != set(DocumentKind)
        ):
            raise ValueError("Invalid J-stage document scope")
        # A direct J invocation must be as private as the complete workflow.
        with tracing_context(enabled=False):
            result = self.provider.analyze(request)
        if not isinstance(result, ProviderResult):
            raise ValueError("Invalid provider result type")
        findings = validate_findings(result.findings)
        seen: set[tuple[str, frozenset[str]]] = set()
        for item in findings.items:
            # Do not merge conflicting recommendations or silently drop duplicates.
            key = (
                " ".join(item.item.split()).casefold(),
                frozenset(str(evidence.document_id) for evidence in item.evidence),
            )
            if key in seen:
                raise ValueError("Duplicate J-stage judgment scope")
            seen.add(key)
        return ProviderResult(
            findings, result.input_tokens, result.output_tokens, result.usage_complete
        )
