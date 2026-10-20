import os
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

# Disable automatic tracing before importing any LangChain integration.
os.environ["LANGSMITH_TRACING"] = "false"
os.environ["LANGCHAIN_TRACING_V2"] = "false"

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.middleware.base import RequestResponseEndpoint
from starlette.responses import Response

from app.api.health import router as health
from app.api.jev import router as jev
from app.core.config import settings
from app.core.errors import ProviderFailure
from app.core.logging import install_secret_redaction


@asynccontextmanager
async def lifespan(application: FastAPI) -> AsyncIterator[None]:
    config = settings()
    restore_logging = install_secret_redaction(config)
    try:
        yield
    finally:
        restore_logging()


app = FastAPI(title="InsuranceAI JEV", version="0.1.0", lifespan=lifespan)
app.include_router(health)
app.include_router(jev)


@app.exception_handler(RequestValidationError)
async def invalid_input(request: Request, exc: RequestValidationError) -> JSONResponse:
    # FastAPI's default errors contain the rejected input, including PDF bytes.
    return JSONResponse(status_code=422, content={"detail": "Invalid JEV request"})


@app.exception_handler(ProviderFailure)
async def provider_failure(request: Request, exc: ProviderFailure) -> JSONResponse:
    return JSONResponse(status_code=502, content={"detail": "AI analysis failed"})


@app.middleware("http")
async def bound_body(request: Request, call_next: RequestResponseEndpoint) -> Response:
    limit = 43_000_000
    size = 0
    chunks = []
    async for chunk in request.stream():
        size += len(chunk)
        if size > limit:
            return JSONResponse(status_code=413, content={"detail": "Request too large"})
        chunks.append(chunk)
    request._body = b"".join(chunks)
    return await call_next(request)
