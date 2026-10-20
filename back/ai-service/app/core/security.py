import secrets
from dataclasses import dataclass

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import settings
from app.core.errors import ApiError
from app.schemas.errors import ErrorCode


@dataclass(frozen=True)
class ServicePrincipal:
    name: str = "spring-insai"


def validate_service_token(request: Request) -> ServicePrincipal:
    token = settings().jev_service_token.get_secret_value()
    if not token:
        raise ApiError(ErrorCode.SERVICE_UNAVAILABLE)
    values = request.headers.getlist("Authorization")
    if len(values) != 1 or len(values[0]) > 8192:
        raise ApiError(ErrorCode.UNAUTHORIZED)
    parts = values[0].split(" ")
    if (
        len(parts) != 2
        or parts[0].lower() != "bearer"
        or not parts[1]
        or not secrets.compare_digest(parts[1].encode("utf-8"), token.encode("utf-8"))
    ):
        raise ApiError(ErrorCode.UNAUTHORIZED)
    principal = ServicePrincipal()
    request.state.service_principal = principal
    return principal


def authenticate(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(HTTPBearer(auto_error=False)),
) -> ServicePrincipal:
    # HTTPBearer supplies the OpenAPI security scheme; validation also rejects
    # duplicate headers and runs in middleware before parsing PDF request bodies.
    return validate_service_token(request)
