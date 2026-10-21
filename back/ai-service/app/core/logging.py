"""Redact configured credentials from standard Python log messages and tracebacks."""

import logging
import traceback
from collections.abc import Callable
from typing import Any
from urllib.parse import quote, quote_plus

from app.core.config import Settings


def install_secret_redaction(config: Settings) -> Callable[[], None]:
    values: set[str] = set()
    for secret in (
        config.google_api_key,
        config.openai_api_key,
        config.langsmith_api_key,
        config.jev_service_token,
    ):
        value = secret.get_secret_value()
        if value:
            values.update((value, quote(value, safe=""), quote_plus(value, safe="")))
    secrets = sorted(values, key=len, reverse=True)
    previous = logging.getLogRecordFactory()

    def redact(text: str) -> str:
        for secret in secrets:
            text = text.replace(secret, "[REDACTED]")
        return text

    def factory(*args: Any, **kwargs: Any) -> logging.LogRecord:
        record = previous(*args, **kwargs)
        sensitive_roots = (
            "google.genai",
            "langchain_google_genai",
            "langchain_core",
            "langsmith",
            "pypdf",
            "httpx",
            "httpcore",
            "uvicorn.access",
        )
        if any(
            record.name == root or record.name.startswith(root + ".") for root in sensitive_roots
        ):
            # SDK diagnostics can embed raw model feedback. Keep severity/name,
            # never their payload or traceback in standard logs.
            record.msg = "Provider SDK diagnostic suppressed"
            record.args = ()
            record.exc_info = None
            record.exc_text = None
            record.stack_info = None
            return record
        record.msg = redact(record.getMessage())
        record.args = ()
        if record.exc_info:
            record.exc_text = redact("".join(traceback.format_exception(*record.exc_info)))
            record.exc_info = None
        if record.stack_info:
            record.stack_info = redact(record.stack_info)
        return record

    logging.setLogRecordFactory(factory)

    def restore() -> None:
        if logging.getLogRecordFactory() is factory:
            logging.setLogRecordFactory(previous)

    return restore
