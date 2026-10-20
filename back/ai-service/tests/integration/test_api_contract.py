import json
from pathlib import Path
from unittest.mock import Mock
from uuid import UUID, uuid4

import pytest
from fastapi.testclient import TestClient

from app.api.jev import service
from app.core.errors import InvalidProviderResponse, ProviderFailure, ProviderTimeout
from app.main import app
from app.schemas.errors import ErrorResponse
from app.schemas.jev import JevResponse


@pytest.fixture
def http(monkeypatch):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    with TestClient(app) as client:
        yield client


AUTH = {"Authorization": "Bearer test-token"}


def check_error(response, status, code):
    assert response.status_code == status
    parsed = ErrorResponse.model_validate(response.json())
    assert parsed.error.code == code
    assert str(parsed.request_id) == response.headers["x-request-id"]
    assert response.headers["cache-control"] == "no-store"
    assert "SENTINEL" not in response.text


def test_success_propagates_header_and_body_request_id(http, request_data):
    response = http.post(
        "/internal/jev",
        json=request_data,
        headers={**AUTH, "X-Request-ID": request_data["request_id"]},
    )
    assert response.status_code == 200
    assert response.headers["x-request-id"] == response.json()["request_id"]
    assert response.json()["request_id"] == request_data["request_id"]


def test_body_id_is_used_when_header_absent(http, request_data):
    response = http.post("/internal/jev", json=request_data, headers=AUTH)
    assert response.status_code == 200
    assert response.headers["x-request-id"] == request_data["request_id"]


def test_mismatch_is_rejected_before_model_run(http, request_data):
    runner = Mock()
    app.dependency_overrides[service] = lambda: runner
    header_id = str(uuid4())
    response = http.post(
        "/internal/jev", json=request_data, headers={**AUTH, "X-Request-ID": header_id}
    )
    check_error(response, 422, "REQUEST_ID_MISMATCH")
    assert response.headers["x-request-id"] == header_id
    runner.run.assert_not_called()


def test_invalid_header_is_not_echoed(http):
    response = http.get("/version", headers={"X-Request-ID": "SENTINEL_PATIENT"})
    check_error(response, 400, "INVALID_REQUEST_ID")
    UUID(response.headers["x-request-id"])


def test_invalid_json_is_not_echoed(http):
    response = http.post(
        "/internal/jev",
        content=b'{"SENTINEL_PATIENT":',
        headers={**AUTH, "Content-Type": "application/json"},
    )
    check_error(response, 422, "INVALID_INPUT")


def test_media_type_rejected(http):
    response = http.post("/internal/jev", content="SENTINEL_PATIENT", headers=AUTH)
    check_error(response, 415, "UNSUPPORTED_MEDIA_TYPE")


def test_oversized_declared_body(http):
    response = http.post(
        "/internal/jev",
        content=b"{}",
        headers={**AUTH, "Content-Type": "application/json", "Content-Length": "43000001"},
    )
    check_error(response, 413, "PAYLOAD_TOO_LARGE")


def test_streamed_size_limit_without_content_length(http, monkeypatch, request_data):
    monkeypatch.setattr("app.api.middleware.MAX_REQUEST_BYTES", 100)
    response = http.post(
        "/internal/jev",
        content=iter([json.dumps(request_data).encode()]),
        headers={**AUTH, "Content-Type": "application/json"},
    )
    check_error(response, 413, "PAYLOAD_TOO_LARGE")


@pytest.mark.parametrize(
    "path,method,status,code",
    [
        ("/missing", "get", 404, "NOT_FOUND"),
        ("/internal/jev", "get", 405, "METHOD_NOT_ALLOWED"),
    ],
)
def test_framework_errors_use_same_schema(http, path, method, status, code):
    response = getattr(http, method)(path)
    check_error(response, status, code)


def test_unauthorized_contract(http, request_data):
    response = http.post("/internal/jev", json=request_data)
    check_error(response, 401, "UNAUTHORIZED")
    assert response.headers["www-authenticate"] == "Bearer"


@pytest.mark.parametrize(
    "failure,status,code",
    [
        (ProviderFailure(), 502, "AI_ANALYSIS_FAILED"),
        (ProviderTimeout(), 504, "AI_TIMEOUT"),
        (InvalidProviderResponse(), 502, "INVALID_AI_RESPONSE"),
        (RuntimeError("SENTINEL_PATIENT"), 500, "INTERNAL_ERROR"),
    ],
)
def test_execution_errors_hide_raw_exception(http, request_data, failure, status, code, caplog):
    runner = Mock()
    runner.run.side_effect = failure
    app.dependency_overrides[service] = lambda: runner
    response = http.post("/internal/jev", json=request_data, headers=AUTH)
    check_error(response, status, code)
    assert response.headers["x-request-id"] == request_data["request_id"]
    assert "SENTINEL" not in caplog.text


def test_invalid_response_schema_is_bad_gateway(http, request_data):
    runner = Mock()
    runner.run.return_value = {"private_data": "SENTINEL_PATIENT"}
    app.dependency_overrides[service] = lambda: runner
    check_error(
        http.post("/internal/jev", json=request_data, headers=AUTH), 502, "INVALID_AI_RESPONSE"
    )


def test_openapi_describes_actual_error_contract_and_bearer_auth(http):
    schema = http.get("/openapi.json").json()
    operation = schema["paths"]["/internal/jev"]["post"]
    for status in (400, 401, 403, 413, 415, 422, 429, 500, 502, 503, 504):
        assert operation["responses"][str(status)]["content"]["application/json"]["schema"] == {
            "$ref": "#/components/schemas/ErrorResponse"
        }
    assert operation["security"] == [{"HTTPBearer": []}]
    assert any(parameter["name"] == "X-Request-ID" for parameter in operation["parameters"])


def test_shared_java_python_response_contract():
    path = Path(__file__).resolve().parents[4] / "docs/ai/contracts/jev-response-example.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    assert JevResponse.model_validate(payload).model_dump(mode="json") == payload


@pytest.mark.parametrize(
    "changes",
    [
        {"submission_status": "APPROVED"},
        {"regulations_status": "SATISFIED"},
        {"provider": "unknown"},
        {"input_tokens": -1},
        {"items": []},
        {"trace_status": "DISABLED"},
        {"trace_id": None},
    ],
)
def test_response_contract_rejects_inconsistent_output(changes):
    path = Path(__file__).resolve().parents[4] / "docs/ai/contracts/jev-response-example.json"
    payload = json.loads(path.read_text(encoding="utf-8"))
    with pytest.raises(ValueError):
        JevResponse.model_validate({**payload, **changes})
