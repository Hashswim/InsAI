"""Final structural V gate. No semantic re-judgment or confidence calibration."""

from dataclasses import dataclass, field

from app.jev.structured_output import validate_findings
from app.providers.base import ProviderResult
from app.schemas.jev import VerificationSummary


@dataclass(frozen=True)
class VerifiedResult(ProviderResult):
    # Server outcome is intentionally separate from the model provider port.
    verification: VerificationSummary = field(kw_only=True)


class VerificationChain:
    def run(self, draft: ProviderResult, *, offline_stub: bool = False) -> VerifiedResult:
        if not isinstance(draft, ProviderResult):
            raise ValueError("Invalid V-stage draft")
        # Recheck evidence presence, bounded finite confidence, nonblank reason,
        # supplement requirements, and normalize UNVERIFIABLE confidence to zero.
        # No numeric cutoff can turn a model self-assessment into an approval.
        findings = validate_findings(draft.findings)
        if type(offline_stub) is not bool:
            raise ValueError("Invalid V-stage execution mode")
        if offline_stub and (
            findings.risk_signals
            or any(item.judgment != "UNVERIFIABLE" or item.evidence for item in findings.items)
        ):
            raise ValueError("Invalid offline stub recommendation")
        return VerifiedResult(
            findings,
            draft.input_tokens,
            draft.output_tokens,
            draft.usage_complete,
            draft.policy_refs,
            draft.agent_runs,
            verification=VerificationSummary(
                method="STRUCTURAL",
                factual_consistency="NOT_VERIFIED",
                confidence_basis="OFFLINE_STUB" if offline_stub else "MODEL_SELF_ASSESSMENT",
                human_review="REQUIRED",
            ),
        )
