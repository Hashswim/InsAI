"""Explicit one-request paid connectivity probe; never logs credentials or raw responses."""

import argparse
import json

import httpx

from app.core.config import settings


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--allow-live", action="store_true")
    args = parser.parse_args()
    if not args.allow_live:
        print(json.dumps({"status": "LIVE_CALL_NOT_AUTHORIZED"}))
        return 1
    try:
        config = settings()
        if config.jev_provider != "openai" or config.openai_model != "gpt-6-luna":
            raise ValueError()
        payload = {
            "model": config.openai_model,
            "store": False,
            "stream": False,
            "reasoning": {"effort": "none"},
            "max_output_tokens": 128,
            "input": "Synthetic connectivity test only. Return ready=true.",
            "text": {
                "verbosity": "low",
                "format": {
                    "type": "json_schema",
                    "name": "connectivity",
                    "strict": True,
                    "schema": {
                        "type": "object",
                        "properties": {"ready": {"type": "boolean"}},
                        "required": ["ready"],
                        "additionalProperties": False,
                    },
                },
            },
        }
        with httpx.Client(timeout=30, trust_env=False, follow_redirects=False) as client:
            response = client.post(
                "https://api.openai.com/v1/responses",
                json=payload,
                headers={"Authorization": "Bearer " + config.openai_api_key.get_secret_value()},
            )
        result: dict[str, object] = {"http_status": response.status_code, "model": "gpt-6-luna"}
        if response.status_code != 200:
            result["status"] = {
                401: "AUTH_FAILED",
                403: "ACCESS_DENIED",
                404: "MODEL_UNAVAILABLE",
                429: "QUOTA_OR_RATE_LIMIT",
            }.get(response.status_code, "API_FAILED")
            print(json.dumps(result))
            return 1
        data = response.json()
        texts = [
            part.get("text", "")
            for item in data.get("output", [])
            if item.get("type") == "message"
            for part in item.get("content", [])
            if part.get("type") == "output_text"
        ]
        success = (
            data.get("status") == "completed"
            and len(texts) == 1
            and json.loads(texts[0]) == {"ready": True}
        )
        result["status"] = "CONNECTED" if success else "INVALID_RESPONSE"
        usage = data.get("usage", {})
        for key in ("input_tokens", "output_tokens"):
            if type(usage.get(key)) is int:
                result[key] = usage[key]
        print(json.dumps(result))
        return 0 if success else 1
    except Exception:
        print(json.dumps({"status": "CONFIGURATION_OR_NETWORK_FAILED"}))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
