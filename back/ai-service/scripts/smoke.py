"""Start the installed service and check HTTP routes without model credentials."""

import json
import os
import socket
import subprocess
import sys
import tempfile
import time
from importlib.resources import files
from urllib.error import URLError
from urllib.request import urlopen


def main() -> None:
    prompt = files("app").joinpath("chains/prompts/jev.txt").read_text(encoding="utf-8")
    if "Judgment" not in prompt:
        raise RuntimeError("Packaged JEV prompt is missing")
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    env = dict(os.environ)
    env.pop("PYTHONPATH", None)
    for name in ("JEV_SECRETS_DIR", "GOOGLE_API_KEY", "LANGSMITH_API_KEY", "JEV_SERVICE_TOKEN"):
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
            ):
                raise RuntimeError("Unexpected readiness response")
            with urlopen(f"http://127.0.0.1:{port}/version", timeout=2) as response:
                info = json.load(response)
            if info["version"] != schema["info"]["version"]:
                raise RuntimeError("Version and OpenAPI metadata disagree")
            print("Smoke OK: installed prompt, HTTP live/ready/version, OpenAPI JEV route")
        finally:
            process.terminate()
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


if __name__ == "__main__":
    main()
