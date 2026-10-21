import asyncio
import json
from unittest.mock import AsyncMock, Mock
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from starlette.requests import Request

from app.api.middleware import http_context
from app.main import app

AUTH = {"Authorization": "Bearer test-token", "Content-Type": "application/json"}


@pytest.mark.parametrize(
    "mutation",
    [
        "root_duplicate",
        "nested_duplicate",
        "escaped_duplicate",
        "nan",
        "infinity",
        "depth",
        "utf16",
        "invalid_utf8",
        "two_objects",
    ],
)
def test_ambiguous_json_is_rejected_before_pdf_or_model(
    monkeypatch, request_data, mutation, caplog
):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    body = json.dumps(request_data)
    if mutation == "root_duplicate":
        body = '{"request_id":"' + request_data["request_id"] + '",' + body[1:]
    elif mutation == "nested_duplicate":
        body = body.replace('"kind": "DIAGNOSIS"', '"kind":"DIAGNOSIS","kind":"DIAGNOSIS"')
    elif mutation == "escaped_duplicate":
        body = '{"\\u0072equest_id":"' + request_data["request_id"] + '",' + body[1:]
    elif mutation in {"nan", "infinity"}:
        body = body[:-1] + ',"SENTINEL_PATIENT":' + ("NaN" if mutation == "nan" else "1e999") + "}"
    elif mutation == "depth":
        body = body[:-1] + ',"SENTINEL_PATIENT":' + "[" * 33 + "0" + "]" * 33 + "}"
    elif mutation == "utf16":
        body = body.encode("utf-16")
    elif mutation == "invalid_utf8":
        body = body.encode() + b"\xff"
    else:
        body += "{}"
    pdf = Mock()
    adapter = Mock()
    monkeypatch.setattr("app.schemas.jev.PdfReader", pdf)
    monkeypatch.setattr("app.api.jev.build_adapter", adapter)
    with TestClient(app) as client:
        response = client.post("/internal/jev", content=body, headers=AUTH)
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "INVALID_INPUT"
    assert "SENTINEL" not in response.text + caplog.text
    assert response.headers["cache-control"] == "no-store"
    assert response.headers["x-content-type-options"] == "nosniff"
    pdf.assert_not_called()
    adapter.assert_not_called()


@pytest.mark.parametrize(
    "headers,status",
    [
        ([(b"content-type", b"application/json"), (b"content-type", b"text/plain")], 415),
        ([(b"content-type", b"application/json; charset = utf-16")], 415),
        ([(b"content-encoding", b"gzip")], 415),
        ([(b"content-encoding", b"identity"), (b"content-encoding", b"identity")], 415),
        ([(b"content-length", b"1"), (b"content-length", b"1")], 422),
        ([(b"content-length", b"1"), (b"transfer-encoding", b"chunked")], 422),
        ([(b"transfer-encoding", b"gzip")], 422),
        ([(b"x-request-id", b"bad"), (b"x-request-id", b"bad")], 400),
    ],
)
def test_ambiguous_headers_are_rejected_without_reading_body(monkeypatch, headers, status):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    if not any(key == b"content-type" for key, _ in headers):
        headers = [(b"content-type", b"application/json"), *headers]
    receive = AsyncMock(side_effect=AssertionError("Body must not be read"))
    request = Request(
        {
            "type": "http",
            "method": "POST",
            "path": "/internal/jev",
            "headers": [(b"authorization", b"Bearer test-token"), *headers],
            "scheme": "http",
            "server": ("test", 80),
            "query_string": b"",
        },
        receive=receive,
    )
    handler = AsyncMock()
    response = asyncio.run(http_context(request, handler))
    assert response.status_code == status
    receive.assert_not_called()
    handler.assert_not_called()


def test_request_id_mismatch_is_rejected_before_pdf_and_sdk(monkeypatch, request_data):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    parser, adapter = Mock(), Mock()
    monkeypatch.setattr("app.schemas.jev.PdfReader", parser)
    monkeypatch.setattr("app.api.jev.build_adapter", adapter)
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev", json=request_data, headers={**AUTH, "X-Request-ID": str(uuid4())}
        )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "REQUEST_ID_MISMATCH"
    parser.assert_not_called()
    adapter.assert_not_called()


def test_public_routes_have_a_smaller_body_bound(monkeypatch):
    monkeypatch.setattr("app.api.middleware.MAX_OTHER_REQUEST_BYTES", 10)
    with TestClient(app) as client:
        response = client.request("GET", "/health/live", content=b"x" * 11)
        healthy = client.get("/health/live")
    assert response.status_code == 413
    assert healthy.status_code == 200
    assert healthy.headers["cache-control"] == "no-store"
    assert healthy.headers["x-content-type-options"] == "nosniff"


def test_declared_content_length_must_match_received_bytes(monkeypatch):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev", content=b"{}", headers={**AUTH, "Content-Length": "1"}
        )
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "INVALID_INPUT"


@pytest.mark.parametrize(
    "media",
    [
        'application/json; charset="utf-8',
        "application/json; charset=utf-8; charset=utf-8",
        "application/json; profile=arbitrary",
        "application/json; charset",
    ],
)
def test_malformed_content_type_parameters_are_not_guessed(monkeypatch, media):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev", content=b"{}", headers={**AUTH, "Content-Type": media}
        )
    assert response.status_code == 415


def test_invalid_body_releases_concurrency_admission(monkeypatch, request_data):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    monkeypatch.setenv("JEV_MAX_CONCURRENT_REQUESTS", "1")
    with TestClient(app) as client:
        assert client.post("/internal/jev", content=b"{", headers=AUTH).status_code == 422
        assert client.post("/internal/jev", json=request_data, headers=AUTH).status_code == 200


def test_asgi_mount_prefix_still_authenticates_before_body(monkeypatch):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    receive = AsyncMock(side_effect=AssertionError("Body must not be read"))
    request = Request(
        {
            "type": "http",
            "method": "POST",
            "path": "/proxy/internal/jev",
            "root_path": "/proxy",
            "headers": [(b"authorization", b"Bearer wrong")],
            "scheme": "http",
            "server": ("test", 80),
            "query_string": b"",
        },
        receive=receive,
    )
    handler = AsyncMock()
    response = asyncio.run(http_context(request, handler))
    assert response.status_code == 401
    receive.assert_not_called()
    handler.assert_not_called()


@pytest.mark.parametrize(
    "media",
    ["application/json", 'application/json; charset="UTF-8"', "application/json; charset = utf8"],
)
def test_utf8_json_and_identity_encoding_are_supported(monkeypatch, request_data, media):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev",
            json=request_data,
            headers={**AUTH, "Content-Type": media, "Content-Encoding": "identity"},
        )
    assert response.status_code == 200
    assert response.headers["cache-control"] == "no-store"
    assert response.headers["x-content-type-options"] == "nosniff"
