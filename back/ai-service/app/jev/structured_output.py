"""Bounded, non-repairing JSON decoding and shared JEV validation.

Never log or propagate JSON/schema exceptions: they can contain medical text.
"""

from typing import Any

from app.core.json_guard import MAX_JSON_DEPTH as MAX_JSON_DEPTH
from app.core.json_guard import decode_json_object as guarded_json_object
from app.schemas.jev import Findings

MAX_FINDINGS_BYTES = 1024 * 1024
MAX_PROVIDER_RESPONSE_BYTES = 4 * MAX_FINDINGS_BYTES
INVALID_OUTPUT = "Invalid structured JEV output"


def decode_json_object(text: object, *, max_bytes: int = MAX_FINDINGS_BYTES) -> dict[str, Any]:
    try:
        return guarded_json_object(text, max_bytes=max_bytes)
    except ValueError:
        raise ValueError(INVALID_OUTPUT) from None


def validate_findings(data: object) -> Findings:
    try:
        if isinstance(data, Findings):
            # Revalidate even a constructed/mutated SDK model; silence dump warnings.
            data = data.model_dump(warnings=False)
        if not isinstance(data, dict):
            raise ValueError()
        findings = Findings.model_validate(data)
        if len(findings.model_dump_json().encode("utf-8")) > MAX_FINDINGS_BYTES:
            raise ValueError()
        return findings
    except (ValueError, TypeError, RecursionError):
        raise ValueError(INVALID_OUTPUT) from None


def parse_findings(text: object) -> Findings:
    return validate_findings(decode_json_object(text))
