"""One explicitly authorized live run on generator-owned synthetic PDFs, no raw telemetry."""

import argparse
import base64
import json
from pathlib import Path
from uuid import uuid4

from app.api.jev import service
from app.chains.release import ACTIVE_RELEASE
from app.core.config import settings
from app.core.logging import install_secret_redaction
from app.schemas.jev import JevRequest


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--allow-live", action="store_true")
    parser.add_argument("--dataset", type=Path, required=True)
    args = parser.parse_args()
    if not args.allow_live:
        print(json.dumps({"status": "LIVE_CALL_NOT_AUTHORIZED"}))
        return 1
    try:
        manifest = json.loads((args.dataset / "manifest.json").read_text(encoding="utf-8"))
        if manifest.get("is_synthetic") is not True:
            raise ValueError()
        config = settings()
        if (
            config.jev_provider != "openai"
            or config.langsmith_enabled
            or config.jev_max_attempts != 1
        ):
            raise ValueError()
        install_secret_redaction(config)
        documents = []
        for doc in manifest["documents"]:
            path = (args.dataset / doc["path"]).resolve()
            if path.parent != args.dataset.resolve():
                raise ValueError()
            documents.append(
                {
                    "document_id": doc["document_id"],
                    "kind": doc["kind"],
                    "content_base64": base64.b64encode(path.read_bytes()).decode("ascii"),
                }
            )
        request = JevRequest.model_validate({"request_id": str(uuid4()), "documents": documents})
        # Ground truth / patient fields / scenario are NEVER supplied to the model.
        result = service().run(request)
        report = {
            "status": "LIVE_SYNTHETIC_JEV_COMPLETED",
            "synthetic": True,
            "provider": result.provider,
            "model": result.model,
            "submission_status": result.submission_status,
            **ACTIVE_RELEASE.metadata(),
            "item_count": len(result.items),
            "input_tokens": result.input_tokens,
            "output_tokens": result.output_tokens,
            "trace_status": result.trace_status,
            "judgments": {
                label: sum(item.judgment == label for item in result.items)
                for label in ("SUITABLE", "NEEDS_SUPPLEMENT", "UNVERIFIABLE")
            },
        }
        output = args.dataset / "live-review-result.json"
        if output.exists():
            raise ValueError()
        output.write_text(result.model_dump_json(indent=2), encoding="utf-8")
        print(json.dumps(report, ensure_ascii=False))
        return 0
    except Exception as error:
        # Class names are from the local fixed exception hierarchy; never print messages/traceback.
        from app.core.errors import ProviderFailure

        status = (
            "PROVIDER_FAILED"
            if isinstance(error, ProviderFailure)
            else "LOCAL_VALIDATION_OR_EXECUTION_FAILED"
        )
        print(json.dumps({"status": status, "raw_error_hidden": True}))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
