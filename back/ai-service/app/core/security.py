import secrets

from fastapi import Header, HTTPException

from app.core.config import settings


def authenticate(authorization: str = Header(default="")) -> None:
    token = settings().jev_service_token.get_secret_value()
    if not token:
        raise HTTPException(503, "JEV service authentication is not configured")
    if not secrets.compare_digest(
        authorization.encode("utf-8"), ("Bearer " + token).encode("utf-8")
    ):
        raise HTTPException(401, "Unauthorized")
