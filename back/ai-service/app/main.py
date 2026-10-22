import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

# Disable automatic tracing before importing any LangChain integration.
os.environ["LANGSMITH_TRACING"] = "false"
os.environ["LANGCHAIN_TRACING_V2"] = "false"

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError, ResponseValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException

from app.api.health import router as health
from app.api.jev import router as jev
from app.api.middleware import http_context
from app.core.config import settings
from app.core.errors import (
    ApiError,
    CircuitOpen,
    InvalidProviderResponse,
    ProviderFailure,
    ProviderTimeout,
    UsageLimitExceeded,
    UsagePolicyUnavailable,
    error_response,
)
from app.core.logging import install_secret_redaction
from app.schemas.errors import ErrorCode
from app.version import SERVICE_VERSION


@asynccontextmanager
async def lifespan(application: FastAPI) -> AsyncIterator[None]:
    config = settings()
    restore_logging = install_secret_redaction(config)
    application.state.ready = True
    try:
        yield
    finally:
        application.state.ready = False
        restore_logging()


app = FastAPI(title="InsuranceAI JEV", version=SERVICE_VERSION, lifespan=lifespan)
app.include_router(health)
app.include_router(jev)


@app.exception_handler(RequestValidationError)
async def invalid_input(request: Request, exc: RequestValidationError) -> JSONResponse:
    # FastAPI's default errors contain the rejected input, including PDF bytes.
    return error_response(request, ErrorCode.INVALID_INPUT)


@app.exception_handler(ProviderFailure)
async def provider_failure(request: Request, exc: ProviderFailure) -> JSONResponse:
    if isinstance(exc, UsageLimitExceeded):
        code = ErrorCode.RATE_LIMITED
    elif isinstance(exc, (CircuitOpen, UsagePolicyUnavailable)):
        code = ErrorCode.SERVICE_UNAVAILABLE
    elif isinstance(exc, ProviderTimeout):
        code = ErrorCode.AI_TIMEOUT
    elif isinstance(exc, InvalidProviderResponse):
        code = ErrorCode.INVALID_AI_RESPONSE
    else:
        code = ErrorCode.AI_ANALYSIS_FAILED
    response = error_response(request, code)
    if isinstance(exc, UsageLimitExceeded) and exc.retry_after is not None:
        response.headers["Retry-After"] = str(exc.retry_after)
    return response


@app.exception_handler(ApiError)
async def api_error(request: Request, exc: ApiError) -> JSONResponse:
    return error_response(request, exc.code)


@app.exception_handler(ResponseValidationError)
async def invalid_output(request: Request, exc: ResponseValidationError) -> JSONResponse:
    return error_response(request, ErrorCode.INVALID_AI_RESPONSE)


@app.exception_handler(HTTPException)
async def http_error(request: Request, exc: HTTPException) -> JSONResponse:
    codes = {
        401: ErrorCode.UNAUTHORIZED,
        403: ErrorCode.FORBIDDEN,
        404: ErrorCode.NOT_FOUND,
        405: ErrorCode.METHOD_NOT_ALLOWED,
        429: ErrorCode.RATE_LIMITED,
        503: ErrorCode.SERVICE_UNAVAILABLE,
    }
    response = error_response(request, codes.get(exc.status_code, ErrorCode.INTERNAL_ERROR))
    if exc.status_code == 405 and exc.headers and "Allow" in exc.headers:
        response.headers["Allow"] = exc.headers["Allow"]
    return response


app.middleware("http")(http_context)
