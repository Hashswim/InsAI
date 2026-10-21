"""Offline structural regression examples; never an assessment of medical accuracy."""

import base64
from dataclasses import dataclass
from io import BytesIO
from uuid import UUID

from pypdf import PdfWriter

from app.chains.jev_workflow import run_workflow
from app.chains.orchestrator import WorkflowProgress, WorkflowStage
from app.chains.release import ACTIVE_RELEASE
from app.providers.base import ProviderRequest, ProviderResult
from app.schemas.jev import LEGACY_DOCUMENT_KINDS, Findings, JevRequest


@dataclass
class SyntheticProvider:
    findings: Findings
    name: str = "synthetic"
    model: str = "offline-structural-v1"

    def analyze(self, request: ProviderRequest) -> ProviderResult:
        return ProviderResult(self.findings, 0, 0)


def evaluate_synthetic() -> dict[str, object]:
    writer = PdfWriter()
    writer.add_blank_page(width=72, height=72)
    buffer = BytesIO()
    writer.write(buffer)
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    request = JevRequest.model_validate(
        {
            "request_id": str(UUID(int=1)),
            "documents": [
                {
                    "document_id": str(UUID(int=index + 2)),
                    "kind": kind.value,
                    "content_base64": encoded,
                }
                for index, kind in enumerate(LEGACY_DOCUMENT_KINDS)
            ],
        }
    )
    checks = []
    for case in ("suitable", "supplement", "unverifiable", "foreign_document", "invalid_page"):
        judgment = (
            "UNVERIFIABLE"
            if case == "unverifiable"
            else "NEEDS_SUPPLEMENT"
            if case == "supplement"
            else "SUITABLE"
        )
        findings = Findings.model_validate(
            {
                "items": [
                    {
                        "item": "synthetic-field",
                        "judgment": judgment,
                        "evidence": []
                        if case == "unverifiable"
                        else [
                            {
                                "document_id": str(
                                    UUID(int=99 if case == "foreign_document" else 2)
                                ),
                                "page": 2 if case == "invalid_page" else 1,
                                "description": "synthetic-only",
                            }
                        ],
                        "confidence": 0.0 if case == "unverifiable" else 0.8,
                        "verification_reason": "synthetic-only",
                        "supplement": "review" if case == "supplement" else "",
                    }
                ],
                "risk_signals": [],
                "summary": "synthetic-only",
            }
        )
        expected = case not in {"foreign_document", "invalid_page"}
        progress = WorkflowProgress()
        try:
            result = run_workflow(request, SyntheticProvider(findings), progress=progress)
            passed = (
                expected
                and result.verification.factual_consistency == "NOT_VERIFIED"
                and result.findings.items[0].judgment == judgment
            )
        except ValueError:
            passed = not expected and progress.report().failed == WorkflowStage.EVIDENCE
        except Exception:
            passed = False
        checks.append({"case": case, "passed": passed})
    return {
        "schema_version": 1,
        "mode": "OFFLINE_SYNTHETIC",
        **ACTIVE_RELEASE.metadata(),
        "total": len(checks),
        "passed": sum(bool(check["passed"]) for check in checks),
        "checks": checks,
    }
