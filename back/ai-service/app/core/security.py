import secrets

from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import settings


def authenticate(
    credentials: HTTPAuthorizationCredentials | None = Depends(HTTPBearer(auto_error=False)),
) -> None:
    token = settings().jev_service_token.get_secret_value()
    if not token:
        raise HTTPException(503, "JEV service authentication is not configured")
    if credentials is None or not secrets.compare_digest(
        credentials.credentials.encode("utf-8"), token.encode("utf-8")
    ):
        raise HTTPException(401, "Unauthorized")
