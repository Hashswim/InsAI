"""Final explicit telemetry boundary. Never accept arbitrary dictionaries from extensions."""

import re
from collections.abc import Mapping
from decimal import Decimal

from app.chains.release import ACTIVE_RELEASE

CONSTANTS = {
    **{key: {value} for key, value in ACTIVE_RELEASE.metadata().items()},
    "provider": {"gemini", "openai", "stub"},
    "routing_policy": {"pinned", "priority"},
    "routing_reason": {
        "OFFLINE_STUB",
        "SAME_PROVIDER_FALLBACK",
        "PINNED_MODEL",
        "FIRST_COMPATIBLE_PRIORITY",
    },
    "router_version": {"jev-router-v1"},
    "cost_accounting": {"DISABLED", "RESERVED_BOUND", "USAGE_ESTIMATE"},
    "verification_method": {"STRUCTURAL"},
    "verification_factual_consistency": {"NOT_VERIFIED"},
    "verification_confidence_basis": {"OFFLINE_STUB", "MODEL_SELF_ASSESSMENT"},
    "verification_human_review": {"REQUIRED"},
    "orchestrator_version": {"jev-orchestrator-v1"},
    "last_workflow_completed_stages": {"", "PREPARE", "PREPARE,J", "PREPARE,J,E", "PREPARE,J,E,V"},
    "last_workflow_failed_stage": {"NONE", "PREPARE", "J", "E", "V"},
    "last_workflow_status": {"FAILED", "COMPLETED", "INCOMPLETE"},
}
COUNTS = {
    "input_tokens",
    "output_tokens",
    "routing_priority_index",
    "execution_attempts",
    "execution_retries",
    "execution_fallbacks",
    "execution_circuit_skips",
}
WORKFLOW_KEYS = {
    key for key in CONSTANTS if key.startswith("last_workflow_") or key == "orchestrator_version"
}
ROUTE_KEYS = {"routing_policy", "routing_reason", "router_version", "routing_priority_index"}
EXECUTION_KEYS = (
    {key for key in COUNTS if key.startswith("execution_")}
    | WORKFLOW_KEYS
    | {"cost_estimate_usd", "cost_accounting"}
)


def component(values: Mapping[str, int | str], allowed: set[str]) -> dict[str, object]:
    if not set(values) <= allowed:
        raise ValueError("Unsafe telemetry component")
    return checked_metadata(dict(values))


def checked_metadata(values: dict[str, object]) -> dict[str, object]:
    for key, value in values.items():
        valid = False
        if key in CONSTANTS:
            valid = type(value) is str and value in CONSTANTS[key]
        elif key in COUNTS:
            valid = type(value) is int and 0 <= value <= 2**63 - 1
        elif key == "model":
            valid = (
                type(value) is str
                and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}", value) is not None
            )
        elif key == "cost_estimate_usd" and type(value) is str and len(value) <= 64:
            amount = Decimal(value)
            valid = amount.is_finite() and amount >= 0
        if not valid:
            raise ValueError("Unsafe telemetry metadata")
    return dict(values)
