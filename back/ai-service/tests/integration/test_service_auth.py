import asyncio
from unittest.mock import AsyncMock, Mock

import pytest
from fastapi.testclient import TestClient
from starlette.requests import Request

from app.api.middleware import http_context
from app.main import app


@pytest.mark.parametrize(
    "headers",
    [
        {},
        {"Authorization": "Bearer wrong-token"},
        {"Authorization": "Basic test-token"},
        {"X-User-Id": "11111111-1111-4111-8111-111111111111", "X-User-Roles": "ROLE_ADMIN"},
        {"Authorization": "Bearer a.user.jwt"},
        {"Authorization": "Bearer "},
    ],
)
def test_untrusted_callers_cannot_initialize_model_or_parse_pdf(monkeypatch, request_data, headers):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    provider = Mock()
    parser = Mock()
    monkeypatch.setattr("app.providers.gemini_provider.ChatGoogleGenerativeAI", provider)
    monkeypatch.setattr("app.schemas.jev.PdfReader", parser)
    with TestClient(app) as client:
        response = client.post("/internal/jev", json=request_data, headers=headers)
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "UNAUTHORIZED"
    provider.assert_not_called()
    parser.assert_not_called()


def test_duplicate_authorization_is_rejected(monkeypatch, request_data):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    with TestClient(app) as client:
        response = client.post(
            "/internal/jev",
            json=request_data,
            headers=[
                ("Authorization", "Bearer test-token"),
                ("Authorization", "Bearer wrong-token"),
            ],
        )
    assert response.status_code == 401


def test_query_token_cannot_authenticate(monkeypatch, request_data):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    with TestClient(app) as client:
        response = client.post("/internal/jev?access_token=test-token", json=request_data)
    assert response.status_code == 401


def test_authentication_precedes_reading_the_body(monkeypatch):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    receive = AsyncMock(side_effect=AssertionError("Request body must not be read"))
    request = Request(
        {
            "type": "http",
            "method": "POST",
            "path": "/internal/jev",
            "headers": [(b"authorization", b"Bearer wrong-token")],
            "scheme": "http",
            "server": ("test", 80),
            "query_string": b"",
        },
        receive=receive,
    )
    next_handler = AsyncMock(side_effect=AssertionError("Routing must not run"))
    response = asyncio.run(http_context(request, next_handler))
    assert response.status_code == 401
    receive.assert_not_called()
    next_handler.assert_not_called()


def test_trailing_slash_cannot_bypass_auth(monkeypatch, request_data):
    monkeypatch.setenv("JEV_SERVICE_TOKEN", "test-token")
    with TestClient(app) as client:
        response = client.post("/internal/jev/", json=request_data, follow_redirects=False)
    assert response.status_code == 401


def test_service_token_must_differ_from_provider_key():
    from app.core.config import Settings

    with pytest.raises(ValueError):
        Settings(jev_service_token="same-key", google_api_key="same-key")
