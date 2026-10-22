from uuid import UUID, uuid4

from fastapi import Request
from starlette.middleware.base import RequestResponseEndpoint
from starlette.responses import Response

from app.core.errors import ApiError, UsageLimitExceeded, error_response
from app.core.json_guard import decode_json_object
from app.core.security import validate_service_token
from app.core.usage import usage_guard
from app.schemas.errors import ErrorCode

MAX_REQUEST_BYTES = 43_000_000
MAX_OTHER_REQUEST_BYTES = 64 * 1024


def json_media_type(value: str) -> bool:
    parts = [part.strip().lower() for part in value.split(";")]
    if parts[0] != "application/json" or len(parts) > 2:
        return False
    if len(parts) == 2:
        name, separator, charset = parts[1].partition("=")
        charset = charset.strip()
        if charset.startswith('"') and charset.endswith('"'):
            charset = charset[1:-1]
        return bool(separator) and name.strip() == "charset" and charset in {"utf-8", "utf8"}
    return True


def secure_response(request: Request, response: Response) -> Response:
    response.headers["X-Request-ID"] = str(request.state.request_id)
    response.headers["Cache-Control"] = "no-store"
    response.headers["X-Content-Type-Options"] = "nosniff"
    return response


async def http_context(request: Request, call_next: RequestResponseEndpoint) -> Response:
    request.state.request_id = uuid4()
    request_ids = request.headers.getlist("X-Request-ID")
    if len(request_ids) > 1:
        return secure_response(request, error_response(request, ErrorCode.INVALID_REQUEST_ID))
    raw_id = request_ids[0] if request_ids else None
    admission = None
    guard = None
    if raw_id is not None:
        try:
            if len(raw_id) != 36:
                raise ValueError()
            parsed = UUID(raw_id)
            if str(parsed) != raw_id.lower():
                raise ValueError()
            request.state.request_id = parsed
        except ValueError:
            return secure_response(request, error_response(request, ErrorCode.INVALID_REQUEST_ID))
    try:
        # Match router scope even when the app is mounted behind an ASGI prefix.
        path = request.scope["path"]
        root = request.scope.get("root_path", "")
        if root and (path == root or path.startswith(root + "/")):
            path = path[len(root) :]
        is_jev = request.method == "POST" and path.rstrip("/") == "/internal/jev"
        if is_jev:
            validate_service_token(request)
            guard = usage_guard()
            admission = guard.admit()
            media_values = request.headers.getlist("Content-Type")
            if len(media_values) != 1:
                raise ApiError(ErrorCode.UNSUPPORTED_MEDIA_TYPE)
            if not json_media_type(media_values[0]):
                raise ApiError(ErrorCode.UNSUPPORTED_MEDIA_TYPE)
            encodings = request.headers.getlist("Content-Encoding")
            if len(encodings) > 1 or (encodings and encodings[0].strip().lower() != "identity"):
                raise ApiError(ErrorCode.UNSUPPORTED_MEDIA_TYPE)
        lengths = request.headers.getlist("Content-Length")
        transfers = request.headers.getlist("Transfer-Encoding")
        if (
            len(lengths) > 1
            or len(transfers) > 1
            or (lengths and transfers)
            or (transfers and transfers[0].strip().lower() != "chunked")
        ):
            raise ApiError(ErrorCode.INVALID_INPUT)
        limit = MAX_REQUEST_BYTES if is_jev else MAX_OTHER_REQUEST_BYTES
        length = lengths[0] if lengths else None
        if length is not None:
            if not length.isascii() or not length.isdigit():
                raise ApiError(ErrorCode.INVALID_INPUT)
            if len(length) > 10 or int(length) > limit:
                raise ApiError(ErrorCode.PAYLOAD_TOO_LARGE)
        size = 0
        chunks = []
        async for chunk in request.stream():
            size += len(chunk)
            if size > limit:
                raise ApiError(ErrorCode.PAYLOAD_TOO_LARGE)
            chunks.append(chunk)
        request._body = b"".join(chunks)
        if length is not None and int(length) != size:
            raise ApiError(ErrorCode.INVALID_INPUT)
        if is_jev:
            try:
                body = decode_json_object(
                    request._body.decode("utf-8"), max_bytes=MAX_REQUEST_BYTES
                )
            except ValueError:
                raise ApiError(ErrorCode.INVALID_INPUT) from None
            if raw_id is not None:
                try:
                    body_id = UUID(body["request_id"])
                except (ValueError, TypeError, KeyError, AttributeError):
                    raise ApiError(ErrorCode.INVALID_INPUT) from None
                if body_id != request.state.request_id:
                    raise ApiError(ErrorCode.REQUEST_ID_MISMATCH)
            del body
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
    return secure_response(request, response)
