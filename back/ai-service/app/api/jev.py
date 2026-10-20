from functools import lru_cache
from uuid import UUID

from fastapi import APIRouter, Depends, Header, HTTPException, Request

from app.application.jev_service import JevService
from app.core.config import settings
from app.core.errors import ApiError
from app.core.security import authenticate
from app.observability.langsmith import SafeTrace
from app.providers.router import build_provider
from app.schemas.errors import ErrorCode, ErrorResponse
from app.schemas.jev import JevRequest, JevResponse

router = APIRouter()


@lru_cache
def service() -> JevService:
    config = settings()
    try:
        return JevService(build_provider(config), SafeTrace(config))
    except Exception:
        raise HTTPException(503, "AI provider is unavailable") from None


@router.post(
    "/internal/jev",
    response_model=JevResponse,
    dependencies=[Depends(authenticate)],
    responses={
        200: {"headers": {"X-Request-ID": {"schema": {"type": "string", "format": "uuid"}}}},
        **{
            status: {
                "model": ErrorResponse,
                "headers": {"X-Request-ID": {"schema": {"type": "string", "format": "uuid"}}},
            }
            for status in (400, 401, 403, 413, 415, 422, 429, 500, 502, 503, 504)
        },
    },
    tags=["JEV"],
)
def analyze(
    payload: JevRequest,
    request: Request,
    runner: JevService = Depends(service),
    x_request_id: UUID | None = Header(default=None, alias="X-Request-ID"),
) -> JevResponse:
    if request.headers.get("X-Request-ID") and request.state.request_id != payload.request_id:
        raise ApiError(ErrorCode.REQUEST_ID_MISMATCH)
    request.state.request_id = payload.request_id
    return runner.run(payload)
