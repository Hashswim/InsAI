from app.jev.orchestrator import JevOrchestrator, WorkflowProgress
from app.jev.verification import VerifiedResult
from app.providers.base import Provider
from app.schemas.jev import JevRequest


def run_workflow(
    request: JevRequest, provider: Provider, *, progress: WorkflowProgress | None = None
) -> VerifiedResult:
    return JevOrchestrator().run(request, provider, progress=progress)
