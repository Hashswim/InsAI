from app.chains.evidence import EvidenceChain
from app.chains.judgment import JudgmentChain
from app.chains.verification import VerificationChain, VerifiedResult
from app.providers.base import Provider, ProviderDocument, ProviderRequest
from app.schemas.jev import JevRequest


def run_workflow(request: JevRequest, provider: Provider) -> VerifiedResult:
    model_request = ProviderRequest(
        request_id=request.request_id,
        documents=tuple(
            ProviderDocument(d.document_id, d.kind, d.content_base64) for d in request.documents
        ),
    )
    draft = JudgmentChain(provider).run(model_request)
    grounded = EvidenceChain().run(request, draft)
    return VerificationChain().run(grounded, offline_stub=provider.name == "stub")
