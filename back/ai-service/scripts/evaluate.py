"""Run only built-in synthetic cases; no dataset uploads or arbitrary file inputs."""

import json

from app.observability.evaluation import evaluate_synthetic

if __name__ == "__main__":
    report = evaluate_synthetic()
    print(json.dumps(report, ensure_ascii=True))
    raise SystemExit(0 if report["passed"] == report["total"] else 1)
