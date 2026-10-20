from dataclasses import dataclass, field
from typing import Protocol, runtime_checkable
from uuid import UUID

from app.schemas.jev import DocumentKind, Findings


@dataclass(frozen=True)
class ProviderDocument:
    """Validated document input; never log its contents."""

    document_id: UUID
    kind: DocumentKind
    content_base64: str = field(repr=False)


@dataclass(frozen=True)
class ProviderRequest:
    """Internal model input, independent of HTTP and business identity."""

    request_id: UUID
    documents: tuple[ProviderDocument, ...] = field(repr=False)


@dataclass(frozen=True)
class ProviderResult:
    findings: Findings = field(repr=False)
    input_tokens: int = 0
    output_tokens: int = 0

    def __post_init__(self) -> None:
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
