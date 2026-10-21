from app.chains.evidence import EvidenceChain
from app.chains.judgment import JudgmentChain
from app.providers.base import Provider, ProviderDocument, ProviderRequest, ProviderResult
from app.schemas.jev import JevRequest


def run_workflow(request: JevRequest, provider: Provider) -> ProviderResult:
    model_request = ProviderRequest(
        request_id=request.request_id,
        documents=tuple(
            ProviderDocument(d.document_id, d.kind, d.content_base64) for d in request.documents
        ),
    )
    draft = JudgmentChain(provider).run(model_request)
    return EvidenceChain().run(request, draft)
