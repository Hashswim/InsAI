from dataclasses import dataclass
from typing import Protocol

from app.schemas.jev import Findings, JevRequest


@dataclass
class ProviderResult:
    findings: Findings
    input_tokens: int = 0
    output_tokens: int = 0


class Provider(Protocol):
    name: str
    model: str

    def analyze(self, request: JevRequest) -> ProviderResult: ...
