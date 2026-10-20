from functools import lru_cache

from fastapi import APIRouter, Depends, HTTPException

from app.application.jev_service import JevService
from app.core.config import settings
from app.core.security import authenticate
from app.observability.langsmith import SafeTrace
from app.providers.router import build_provider
from app.schemas.jev import JevRequest, JevResponse

router = APIRouter()


@lru_cache
def service() -> JevService:
    config = settings()
    try:
        return JevService(build_provider(config), SafeTrace(config))
    except Exception:
        raise HTTPException(503, "AI provider is unavailable") from None


@router.post("/internal/jev", response_model=JevResponse, dependencies=[Depends(authenticate)])
def analyze(request: JevRequest, runner: JevService = Depends(service)) -> JevResponse:
    return runner.run(request)
