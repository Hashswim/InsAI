from langsmith import tracing_context

from app.providers.base import Provider, ProviderResult
from app.schemas.jev import Findings, JevRequest


def run_workflow(request: JevRequest, provider: Provider) -> ProviderResult:
    # Force off even when the host has automatic tracing enabled.
    with tracing_context(enabled=False):
        result = provider.analyze(request)
    findings = Findings.model_validate(result.findings.model_dump())
    pages = {d.document_id: d.page_count() for d in request.documents}
    for item in findings.items:
        for evidence in item.evidence:
            if evidence.document_id not in pages or evidence.page > pages[evidence.document_id]:
                raise ValueError("Evidence refers to an unknown document or page")
    result.findings = findings
    return result
