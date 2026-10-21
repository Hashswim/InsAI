"""Single-attempt, fail-fast JEV coordination with payload-free progress."""

from collections.abc import Callable
from dataclasses import dataclass
from enum import Enum
from typing import TypeVar

from app.chains.evidence import EvidenceChain
from app.chains.judgment import JudgmentChain
from app.chains.verification import VerificationChain, VerifiedResult
from app.core.errors import InvalidProviderResponse, TransientProviderFailure
from app.providers.base import Provider, ProviderDocument, ProviderRequest, ProviderResult
from app.schemas.jev import JevRequest


class WorkflowStage(str, Enum):
    PREPARE = "PREPARE"
    JUDGMENT = "J"
    EVIDENCE = "E"
    VERIFICATION = "V"


STAGES = tuple(WorkflowStage)


@dataclass(frozen=True)
class WorkflowReport:
    completed: tuple[WorkflowStage, ...]
    failed: WorkflowStage | None

    def __post_init__(self) -> None:
        if (
            type(self.completed) is not tuple
            or any(type(stage) is not WorkflowStage for stage in self.completed)
            or len(self.completed) > len(STAGES)
            or self.completed != STAGES[: len(self.completed)]
            or (
                self.failed is not None
                and (
                    type(self.failed) is not WorkflowStage
                    or len(self.completed) == len(STAGES)
                    or self.failed != STAGES[len(self.completed)]
                )
            )
        ):
            raise ValueError("Invalid JEV workflow report")

    def metadata(self) -> dict[str, str]:
        return {
            "orchestrator_version": "jev-orchestrator-v1",
            "last_workflow_completed_stages": ",".join(stage.value for stage in self.completed),
            "last_workflow_failed_stage": self.failed.value if self.failed else "NONE",
            "last_workflow_status": (
                "FAILED"
                if self.failed
                else "COMPLETED"
                if self.completed == STAGES
                else "INCOMPLETE"
            ),
        }


class WorkflowProgress:
    """Owned by one attempt. Never retain findings, PDFs, IDs, or exceptions."""

    def __init__(self) -> None:
        self._completed: tuple[WorkflowStage, ...] = ()
        self._active: WorkflowStage | None = None
        self._failed: WorkflowStage | None = None

    def report(self) -> WorkflowReport:
        return WorkflowReport(self._completed, self._failed)

    def begin(self, stage: WorkflowStage) -> None:
        if (
            type(stage) is not WorkflowStage
            or self._active is not None
            or self._failed is not None
            or len(self._completed) == len(STAGES)
            or stage != STAGES[len(self._completed)]
        ):
            raise ValueError("Invalid JEV stage transition")
        self._active = stage

    def complete(self, stage: WorkflowStage) -> None:
        if type(stage) is not WorkflowStage or self._active != stage:
            raise ValueError("Invalid JEV stage transition")
        self._completed += (stage,)
        self._active = None

    def fail(self, stage: WorkflowStage) -> None:
        if type(stage) is not WorkflowStage or self._active != stage:
            raise ValueError("Invalid JEV stage transition")
        self._failed, self._active = stage, None

    def is_fresh(self) -> bool:
        return not self._completed and self._active is None and self._failed is None


T = TypeVar("T")


class JevOrchestrator:
    """Stateless coordinator; retry/routing/budget/persistence stay outside."""

    def run(
        self, request: JevRequest, provider: Provider, *, progress: WorkflowProgress | None = None
    ) -> VerifiedResult:
        state = progress if progress is not None else WorkflowProgress()
        if not state.is_fresh():
            raise ValueError("JEV progress cannot be reused")

        def step(stage: WorkflowStage, action: Callable[[], T]) -> T:
            state.begin(stage)
            try:
                output = action()
            except TransientProviderFailure:
                state.fail(stage)
                # Only the J model call can be replayed by the outer executor.
                if stage != WorkflowStage.JUDGMENT:
                    raise InvalidProviderResponse() from None
                raise
            except BaseException:
                state.fail(stage)
                raise
            state.complete(stage)
            return output

        def prepare() -> tuple[JevRequest, ProviderRequest, bool]:
            if not isinstance(request, JevRequest):
                raise ValueError("Invalid JEV orchestration request")
            # Detach request containers from the caller; PDF strings/UUIDs are immutable.
            snapshot = request.model_copy(deep=True)
            port = ProviderRequest(
                snapshot.request_id,
                tuple(
                    ProviderDocument(d.document_id, d.kind, d.content_base64)
                    for d in snapshot.documents
                ),
            )
            return snapshot, port, provider.name == "stub"

        def judgment() -> ProviderResult:
            result = JudgmentChain(provider).run(port)
            if not isinstance(result, ProviderResult):
                raise ValueError("Invalid J-stage result")
            return result

        def evidence() -> ProviderResult:
            result = EvidenceChain().run(snapshot, draft)
            if not isinstance(result, ProviderResult):
                raise ValueError("Invalid E-stage result")
            return result

        def verification() -> VerifiedResult:
            result = VerificationChain().run(grounded, offline_stub=offline_stub)
            if not isinstance(result, VerifiedResult):
                raise ValueError("Invalid V-stage result")
            return result

        snapshot, port, offline_stub = step(WorkflowStage.PREPARE, prepare)
        draft = step(WorkflowStage.JUDGMENT, judgment)
        grounded = step(WorkflowStage.EVIDENCE, evidence)
        return step(WorkflowStage.VERIFICATION, verification)
