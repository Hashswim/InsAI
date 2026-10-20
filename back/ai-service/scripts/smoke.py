"""Start the installed service and check HTTP routes without model credentials."""

import json
import os
import socket
import subprocess
import sys
import tempfile
import time
from importlib.resources import files
from urllib.error import HTTPError, URLError
from urllib.request import Request as HttpRequest
from urllib.request import urlopen
from uuid import UUID


def main() -> None:
    prompt = files("app").joinpath("chains/prompts/jev.txt").read_text(encoding="utf-8")
    if "Judgment" not in prompt:
        raise RuntimeError("Packaged JEV prompt is missing")
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    env = dict(os.environ)
    env.pop("PYTHONPATH", None)
    for name in (
        "JEV_SECRETS_DIR",
        "GOOGLE_API_KEY",
        "OPENAI_API_KEY",
        "OPENAI_MODEL",
        "JEV_MODEL_CATALOG_PATH",
        "JEV_ROUTING_POLICY",
        "JEV_MODEL_PRIORITY",
        "JEV_MAX_ATTEMPTS",
        "JEV_RETRY_ATTEMPTS",
        "JEV_FALLBACK_ENABLED",
        "JEV_ATTEMPT_TIMEOUT_SECONDS",
        "JEV_EXECUTION_BUDGET_SECONDS",
        "JEV_RETRY_BASE_DELAY_SECONDS",
        "JEV_CIRCUIT_ENABLED",
        "JEV_CIRCUIT_FAILURE_THRESHOLD",
        "JEV_CIRCUIT_COOLDOWN_SECONDS",
        "JEV_LIMITS_ENABLED",
        "JEV_REQUESTS_PER_MINUTE",
        "JEV_ATTEMPTS_PER_MINUTE",
        "JEV_MAX_CONCURRENT_REQUESTS",
        "JEV_MAX_CONCURRENT_CALLS",
        "JEV_COST_GUARD_ENABLED",
        "JEV_REQUEST_BUDGET_USD",
        "JEV_DAILY_BUDGET_USD",
        "JEV_PRICING_MAX_AGE_DAYS",
        "LANGSMITH_API_KEY",
        "JEV_SERVICE_TOKEN",
    ):
        env.pop(name, None)
    env.update(
        {
            "LANGSMITH_TRACING": "false",
            "LANGCHAIN_TRACING_V2": "false",
            "APP_ENV": "test",
            "JEV_PROVIDER": "stub",
            "LANGSMITH_ENABLED": "false",
            "PYDANTIC_SETTINGS_DEBUG": "false",
            "JEV_SERVICE_TOKEN": "synthetic-smoke-token",
            "JEV_REQUESTS_PER_MINUTE": "1",
        }
    )
    with tempfile.TemporaryDirectory(prefix="insai-smoke-") as directory:
        process = subprocess.Popen(
            [
                sys.executable,
                "-m",
                "uvicorn",
                "app.main:app",
                "--host",
                "127.0.0.1",
                "--port",
                str(port),
                "--no-access-log",
            ],
            cwd=directory,
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        try:
            deadline = time.monotonic() + 15
            while True:
                if process.poll() is not None:
                    raise RuntimeError("Service exited before readiness")
                try:
                    with urlopen(f"http://127.0.0.1:{port}/health/live", timeout=1) as response:
                        if json.load(response) != {"status": "UP"}:
                            raise RuntimeError("Unexpected health response")
                    break
                except URLError:
                    if time.monotonic() >= deadline:
                        raise RuntimeError("Service startup timed out") from None
                    time.sleep(0.05)
            with urlopen(f"http://127.0.0.1:{port}/openapi.json", timeout=2) as response:
                schema = json.load(response)
            if "/internal/jev" not in schema["paths"]:
                raise RuntimeError("JEV route is missing")
            with urlopen(f"http://127.0.0.1:{port}/health/ready", timeout=2) as response:
                readiness = json.load(response)
            if (
                readiness["status"] != "READY"
                or readiness["mode"] != "stub"
                or readiness["external_connections"] != "NOT_PROBED"
                or readiness["checks"].get("model_catalog") != "COMPATIBLE"
                or readiness["checks"].get("routing") != "SELECTABLE"
            ):
                raise RuntimeError("Unexpected readiness response")
            with urlopen(f"http://127.0.0.1:{port}/version", timeout=2) as response:
                info = json.load(response)
            if info["version"] != schema["info"]["version"]:
                raise RuntimeError("Version and OpenAPI metadata disagree")
            try:
                urlopen(f"http://127.0.0.1:{port}/missing", timeout=2)
                raise RuntimeError("Missing route unexpectedly succeeded")
            except HTTPError as error:
                failure = json.load(error)
                if error.code != 404 or failure["error"]["code"] != "NOT_FOUND":
                    raise RuntimeError("Unexpected error contract") from None
                if str(UUID(failure["request_id"])) != error.headers["X-Request-ID"]:
                    raise RuntimeError("Error correlation ID mismatch") from None
            for token, status in [
                (None, 401),
                ("wrong-token", 401),
                ("synthetic-smoke-token", 422),
                ("synthetic-smoke-token", 429),
            ]:
                headers = {"Content-Type": "application/json"}
                if token is not None:
                    headers["Authorization"] = "Bearer " + token
                probe = HttpRequest(
                    f"http://127.0.0.1:{port}/internal/jev",
                    data=b'{"incomplete":',
                    headers=headers,
                    method="POST",
                )
                try:
                    urlopen(probe, timeout=2)
                    raise RuntimeError("Authentication probe unexpectedly succeeded")
                except HTTPError as error:
                    failure = json.load(error)
                    expected = {401: "UNAUTHORIZED", 422: "INVALID_INPUT", 429: "RATE_LIMITED"}[
                        status
                    ]
                    if error.code != status or failure["error"]["code"] != expected:
                        raise RuntimeError("Unexpected authentication contract") from None
            print("Smoke OK: HTTP operations, request IDs, service auth and pre-body usage limit")
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


if __name__ == "__main__":
    main()
