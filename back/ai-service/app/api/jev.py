from functools import lru_cache
from uuid import UUID

from fastapi import APIRouter, Depends, Header, HTTPException, Request

from app.application.jev_service import JevService
from app.application.resilient_execution import ResilientExecutor
from app.application.resilient_service import ResilientJevService
from app.core.config import settings
from app.core.errors import ApiError
from app.core.security import authenticate
from app.core.usage import usage_guard, validate_cost_profiles
from app.observability.langsmith import SafeTrace
from app.providers.router import build_adapter
from app.providers.routing_policy import route_candidates
from app.schemas.errors import ErrorCode, ErrorResponse
from app.schemas.jev import JevRequest, JevResponse

router = APIRouter()


@lru_cache
def service() -> JevService:
    config = settings()
    try:
        routes = route_candidates(config)
        validate_cost_profiles(config, routes)
        provider = build_adapter(config, routes[0])
        executor = ResilientExecutor(
            config,
            routes,
            provider,
            lambda route: build_adapter(config, route),
            usage=usage_guard(),
        )
        return ResilientJevService(provider, SafeTrace(config), routes[0], executor)
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
