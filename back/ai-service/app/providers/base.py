from dataclasses import dataclass, field
from typing import Protocol, runtime_checkable
from uuid import UUID

from app.schemas.jev import DocumentKind, Findings, PolicyRef


@dataclass(frozen=True)
class ProviderDocument:
    """Document identity only. PDF bytes never reach a model adapter."""

    document_id: UUID
    kind: DocumentKind


@dataclass(frozen=True)
class ProviderRequest:
    """Internal model input, independent of HTTP and business identity."""

    request_id: UUID
    documents: tuple[ProviderDocument, ...] = field(repr=False)
    # Extracted text form (app.jev.precheck); the only document content a model sees.
    form: str = field(default="", repr=False)


@dataclass(frozen=True)
class AgentRun:
    """One agent or retrieval step inside the J stage: timing and token counts only."""

    name: str
    kind: str  # "llm" | "retriever"
    offset: float  # seconds after the multi-agent run started
    seconds: float
    input_tokens: int = 0
    output_tokens: int = 0


@dataclass(frozen=True)
class ProviderResult:
    findings: Findings = field(repr=False)
    input_tokens: int = 0
    output_tokens: int = 0
    usage_complete: bool = False
    # Multi-agent extras: RAG clause citations and per-agent timing/usage (no content).
    policy_refs: tuple[PolicyRef, ...] = field(default=(), repr=False)
    agent_runs: tuple["AgentRun", ...] = field(default=(), repr=False)

    def __post_init__(self) -> None:
        if type(self.usage_complete) is not bool:
            raise ValueError("Invalid usage completeness flag")
        # Reject coercion (including bool) and invalid usage before tracing it.
        for count in (self.input_tokens, self.output_tokens):
            if type(count) is not int or count < 0:
                raise ValueError("Invalid provider token usage")


@runtime_checkable
class Provider(Protocol):
    """Synchronous, single-attempt JEV structured-output port.

    Adapters own SDK messages/parsing, not authorization, routing, retries,
    persistence or raw tracing. Missing usage is represented by zero.
    """

    name: str
    model: str

    def analyze(self, request: ProviderRequest) -> ProviderResult: ...
