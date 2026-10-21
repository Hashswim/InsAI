"""Bounded strict JSON objects shared by HTTP and model-output boundaries."""

import json
from math import isfinite
from typing import Any, NoReturn

MAX_JSON_DEPTH = 32


def _reject_constant(value: str) -> NoReturn:
    raise ValueError("Invalid JSON object")


def _finite_float(value: str) -> float:
    number = float(value)
    if not isfinite(number):
        raise ValueError("Invalid JSON object")
    return number


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Invalid JSON object")
        result[key] = value
    return result


def _check_depth(text: str) -> None:
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
                raise ValueError("Invalid JSON object")
        elif char in "]}":
            depth -= 1


def decode_json_object(text: object, *, max_bytes: int) -> dict[str, Any]:
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
        raise ValueError("Invalid JSON object") from None
