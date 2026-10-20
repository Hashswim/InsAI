"""Bounded, non-repairing JSON decoding and shared JEV validation.

Never log or propagate JSON/schema exceptions: they can contain medical text.
"""

import json
from math import isfinite
from typing import Any, NoReturn

from app.schemas.jev import Findings

MAX_FINDINGS_BYTES = 1024 * 1024
MAX_PROVIDER_RESPONSE_BYTES = 4 * MAX_FINDINGS_BYTES
MAX_JSON_DEPTH = 32
INVALID_OUTPUT = "Invalid structured JEV output"


def _reject_constant(value: str) -> NoReturn:
    raise ValueError(INVALID_OUTPUT)


def _finite_float(value: str) -> float:
    number = float(value)
    if not isfinite(number):
        raise ValueError(INVALID_OUTPUT)
    return number


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(INVALID_OUTPUT)
        result[key] = value
    return result


def _check_depth(text: str) -> None:
    # Bound nesting before json.loads, ignoring braces inside JSON strings.
    depth = 0
    in_string = escaped = False
    for char in text:
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
        elif char == '"':
            in_string = True
        elif char in "[{":
            depth += 1
            if depth > MAX_JSON_DEPTH:
                raise ValueError(INVALID_OUTPUT)
        elif char in "]}":
            depth -= 1


def decode_json_object(text: object, *, max_bytes: int = MAX_FINDINGS_BYTES) -> dict[str, Any]:
    try:
        if not isinstance(text, str) or len(text) > max_bytes:
            raise ValueError()
        if len(text.encode("utf-8")) > max_bytes:
            raise ValueError()
        _check_depth(text)
        data = json.loads(
            text,
            object_pairs_hook=_unique_object,
            parse_constant=_reject_constant,
            parse_float=_finite_float,
        )
        if not isinstance(data, dict):
            raise ValueError()
        return data
    except (ValueError, TypeError, RecursionError):
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
