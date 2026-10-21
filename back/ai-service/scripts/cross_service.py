"""Two real local HTTP services, synthetic PDFs, temporary storage and H2 only."""

import json
import os
import secrets
import signal
import socket
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from urllib.error import URLError
from urllib.request import urlopen

from pypdf import PdfWriter


def main() -> int:
    root = Path(__file__).resolve().parents[3]
    ai_root = root / "back" / "ai-service"
    spring_root = root / "back" / "insai"
    # Pass only runtime necessities; never inherit model/trace keys, .env settings or JVM agents.
    env = {
        key: os.environ[key]
        for key in (
            "PATH",
            "HOME",
            "USER",
            "LOGNAME",
            "JAVA_HOME",
            "TMPDIR",
            "LANG",
            "LC_ALL",
            "GRADLE_USER_HOME",
        )
        if key in os.environ
    }
    token = secrets.token_urlsafe(32)
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    with tempfile.TemporaryDirectory(prefix="insai-cross-service-") as directory:
        temp = Path(directory)
        pdf = temp / "synthetic.pdf"
        writer = PdfWriter()
        writer.add_blank_page(width=72, height=72)
        writer.write(pdf)
        base_url = f"http://127.0.0.1:{port}"
        child_env = {
            **env,
            "APP_ENV": "test",
            "JEV_PROVIDER": "stub",
            "JEV_SERVICE_TOKEN": token,
            "LANGSMITH_ENABLED": "false",
            "LANGSMITH_TRACING": "false",
            "LANGCHAIN_TRACING_V2": "false",
            "PYDANTIC_SETTINGS_DEBUG": "false",
        }
        server = subprocess.Popen(
            [
                sys.executable,
                "-m",
                "uvicorn",
                "app.main:app",
                "--app-dir",
                str(ai_root),
                "--host",
                "127.0.0.1",
                "--port",
                str(port),
                "--no-access-log",
            ],
            cwd=temp,
            env=child_env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        try:
            deadline = time.monotonic() + 20
            while True:
                if server.poll() is not None:
                    raise RuntimeError("Local synthetic AI server exited before readiness")
                try:
                    with urlopen(base_url + "/health/ready", timeout=1) as response:
                        if response.status == 200:
                            break
                except URLError:
                    pass
                if time.monotonic() >= deadline:
                    raise RuntimeError("Local synthetic AI server readiness timed out")
                time.sleep(0.1)
            result = subprocess.Popen(
                [
                    str(spring_root / "gradlew"),
                    "crossServiceTest",
                    "--no-daemon",
                    "--console=plain",
                ],
                cwd=spring_root,
                env={
                    **env,
                    "CROSS_SERVICE_BASE_URL": base_url,
                    "CROSS_SERVICE_TOKEN": token,
                    "CROSS_SERVICE_PDF": str(pdf),
                    "CROSS_SERVICE_STORAGE": str(temp / "storage"),
                },
                start_new_session=True,
            )
            try:
                result.wait(timeout=180)
            finally:
                if result.poll() is None:
                    # This new process group belongs only to this harness invocation.
                    os.killpg(result.pid, signal.SIGTERM)
                    try:
                        result.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        os.killpg(result.pid, signal.SIGKILL)
                        result.wait(timeout=5)
            report_path = (
                spring_root
                / "build/test-results/crossServiceTest/TEST-com.insuranceai.ai.application.JevCrossServiceTest.xml"
            )
            summary = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
            if result.returncode == 0:
                report = ET.parse(report_path).getroot()
                summary = {key: int(report.get(key, "0")) for key in summary}
            passed = (
                result.returncode == 0
                and summary["tests"] == 4
                and not any(summary[key] for key in ("failures", "errors", "skipped"))
            )
            print(json.dumps({"mode": "LOCAL_STUB_H2", "passed": passed, **summary}))
            return 0 if passed else 1
        finally:
            server.terminate()
            try:
                server.wait(timeout=5)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait(timeout=5)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, RuntimeError, ValueError, ET.ParseError, subprocess.TimeoutExpired):
        # No process command/env/body or raw exception in the harness error output.
        print("Cross-service verification failed; inspect the local test report.", file=sys.stderr)
        raise SystemExit(1) from None
