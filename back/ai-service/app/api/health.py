from typing import Literal

from fastapi import APIRouter, Depends, Request, Response
from pydantic import BaseModel

from app.core.config import Settings, settings
from app.core.errors import UsagePolicyUnavailable
from app.core.resources import load_jev_prompt
from app.core.usage import usage_guard, validate_cost_profiles
from app.providers.routing_policy import route_candidates
from app.version import SERVICE_NAME, SERVICE_VERSION

router = APIRouter()


class Readiness(BaseModel):
    status: Literal["READY", "NOT_READY"]
    mode: Literal["stub", "gemini", "openai"]
    checks: dict[str, str]
    external_connections: Literal["NOT_PROBED"] = "NOT_PROBED"


class VersionInfo(BaseModel):
    service: str
    version: str


@router.get("/health/live", tags=["operations"])
def live(response: Response) -> dict[str, str]:
    response.headers["Cache-Control"] = "no-store"
    return {"status": "UP"}


@router.get(
    "/health/ready",
    response_model=Readiness,
    responses={503: {"model": Readiness}},
    tags=["operations"],
)
def ready(request: Request, response: Response, config: Settings = Depends(settings)) -> Readiness:
    checks = {
        "startup": "COMPLETE" if getattr(request.app.state, "ready", False) else "INCOMPLETE",
        "service_auth": "CONFIGURED" if config.jev_service_token.get_secret_value() else "MISSING",
        "provider": "STUB" if config.jev_provider == "stub" else "CONFIGURED",
    }
    try:
        load_jev_prompt()
        checks["prompt"] = "AVAILABLE"
    except Exception:
        checks["prompt"] = "UNAVAILABLE"
    try:
        routes = route_candidates(config)
        checks["model_catalog"] = "COMPATIBLE"
        checks["routing"] = "SELECTABLE"
        validate_cost_profiles(config, routes)
        if config.jev_cost_guard_enabled and not usage_guard().policy_ready():
            raise UsagePolicyUnavailable()
        checks["cost_policy"] = "CONFIGURED" if config.jev_cost_guard_enabled else "DISABLED"
    except Exception:
        checks["model_catalog"] = "UNAVAILABLE"
        checks["routing"] = "UNAVAILABLE"
        checks["cost_policy"] = "UNAVAILABLE"
    is_ready = (
        checks["startup"] == "COMPLETE"
        and checks["service_auth"] == "CONFIGURED"
        and checks["prompt"] == "AVAILABLE"
        and checks["model_catalog"] == "COMPATIBLE"
        and checks["cost_policy"] != "UNAVAILABLE"
    )
    # LangSmith is optional. No network, model SDK initialization, or paid probes.
    checks["langsmith"] = "CONFIGURED" if config.langsmith_enabled else "DISABLED"
    response.status_code = 200 if is_ready else 503
    response.headers["Cache-Control"] = "no-store"
    return Readiness(
        status="READY" if is_ready else "NOT_READY", mode=config.jev_provider, checks=checks
    )


@router.get("/version", response_model=VersionInfo, tags=["operations"])
def service_version(response: Response) -> VersionInfo:
    response.headers["Cache-Control"] = "no-store"
    return VersionInfo(service=SERVICE_NAME, version=SERVICE_VERSION)
