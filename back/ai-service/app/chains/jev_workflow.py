from langsmith import tracing_context

from app.chains.structured_output import validate_findings
from app.providers.base import Provider, ProviderDocument, ProviderRequest, ProviderResult
from app.schemas.jev import JevRequest


def run_workflow(request: JevRequest, provider: Provider) -> ProviderResult:
    # Force off even when the host has automatic tracing enabled.
    model_request = ProviderRequest(
        request_id=request.request_id,
        documents=tuple(
            ProviderDocument(d.document_id, d.kind, d.content_base64) for d in request.documents
        ),
    )
    with tracing_context(enabled=False):
        result = provider.analyze(model_request)
    if not isinstance(result, ProviderResult):
        raise ValueError("Invalid provider result type")
    findings = validate_findings(result.findings)
    pages = {d.document_id: d.page_count() for d in request.documents}
    for item in findings.items:
        for evidence in item.evidence:
            if evidence.document_id not in pages or evidence.page > pages[evidence.document_id]:
                raise ValueError("Evidence refers to an unknown document or page")
    return ProviderResult(
        findings, result.input_tokens, result.output_tokens, result.usage_complete
    )
