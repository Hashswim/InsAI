from uuid import UUID, uuid4

from fastapi import Request
from starlette.middleware.base import RequestResponseEndpoint
from starlette.responses import Response

from app.core.errors import ApiError, UsageLimitExceeded, error_response
from app.core.security import validate_service_token
from app.core.usage import usage_guard
from app.schemas.errors import ErrorCode

MAX_REQUEST_BYTES = 43_000_000


async def http_context(request: Request, call_next: RequestResponseEndpoint) -> Response:
    request.state.request_id = uuid4()
    raw_id = request.headers.get("X-Request-ID")
    admission = None
    guard = None
    if raw_id is not None:
        try:
            parsed = UUID(raw_id)
            if str(parsed) != raw_id.lower():
                raise ValueError()
            request.state.request_id = parsed
        except ValueError:
            return error_response(request, ErrorCode.INVALID_REQUEST_ID)
    try:
        if request.method == "POST" and request.url.path.rstrip("/") == "/internal/jev":
            validate_service_token(request)
            guard = usage_guard()
            admission = guard.admit()
        if request.method == "POST" and request.url.path == "/internal/jev":
            media = request.headers.get("Content-Type", "").split(";", 1)[0].strip().lower()
            if media != "application/json":
                raise ApiError(ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        length = request.headers.get("Content-Length")
        if length is not None:
            if not length.isascii() or not length.isdigit():
                raise ApiError(ErrorCode.INVALID_INPUT)
            if len(length) > 10 or int(length) > MAX_REQUEST_BYTES:
                raise ApiError(ErrorCode.PAYLOAD_TOO_LARGE)
        size = 0
        chunks = []
        async for chunk in request.stream():
            size += len(chunk)
            if size > MAX_REQUEST_BYTES:
                raise ApiError(ErrorCode.PAYLOAD_TOO_LARGE)
            chunks.append(chunk)
        request._body = b"".join(chunks)
        response = await call_next(request)
    except UsageLimitExceeded as exc:
        response = error_response(request, ErrorCode.RATE_LIMITED)
        if exc.retry_after is not None:
            response.headers["Retry-After"] = str(exc.retry_after)
    except ApiError as exc:
        response = error_response(request, exc.code)
    except Exception:
        # Never expose/log a framework or SDK exception containing request data.
        response = error_response(request, ErrorCode.INTERNAL_ERROR)
    finally:
        if admission is not None and guard is not None:
            guard.release(admission)
    response.headers["X-Request-ID"] = str(request.state.request_id)
    return response
