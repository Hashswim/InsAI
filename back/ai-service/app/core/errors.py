from uuid import UUID, uuid4

from fastapi import Request
from fastapi.responses import JSONResponse

from app.schemas.errors import ErrorCode, ErrorDetail, ErrorResponse

ERRORS: dict[ErrorCode, tuple[int, str]] = {
    ErrorCode.INVALID_REQUEST_ID: (400, "X-Request-ID must be a canonical UUID"),
    ErrorCode.REQUEST_ID_MISMATCH: (422, "Header and body request IDs must match"),
    ErrorCode.INVALID_INPUT: (422, "Invalid JEV request"),
    ErrorCode.PAYLOAD_TOO_LARGE: (413, "Request too large"),
    ErrorCode.UNSUPPORTED_MEDIA_TYPE: (415, "Content-Type must be application/json"),
    ErrorCode.UNAUTHORIZED: (401, "Unauthorized"),
    ErrorCode.FORBIDDEN: (403, "Forbidden"),
    ErrorCode.NOT_FOUND: (404, "Route not found"),
    ErrorCode.METHOD_NOT_ALLOWED: (405, "Method not allowed"),
    ErrorCode.SERVICE_UNAVAILABLE: (503, "AI service is unavailable"),
    ErrorCode.AI_ANALYSIS_FAILED: (502, "AI analysis failed"),
    ErrorCode.INVALID_AI_RESPONSE: (502, "Invalid AI response"),
    ErrorCode.AI_TIMEOUT: (504, "AI analysis timed out"),
    ErrorCode.RATE_LIMITED: (429, "AI service usage limit exceeded"),
    ErrorCode.INTERNAL_ERROR: (500, "Internal server error"),
}


class ApiError(Exception):
    def __init__(self, code: ErrorCode):
        self.code = code
        super().__init__(code.value)


def error_response(request: Request, code: ErrorCode) -> JSONResponse:
    request_id: UUID = getattr(request.state, "request_id", None) or uuid4()
    status, message = ERRORS[code]
    headers = {"X-Request-ID": str(request_id), "Cache-Control": "no-store"}
    if status == 401:
        headers["WWW-Authenticate"] = "Bearer"
    return JSONResponse(
        status_code=status,
        headers=headers,
        content=ErrorResponse(
            request_id=request_id, error=ErrorDetail(code=code, message=message)
        ).model_dump(mode="json"),
    )


class ProviderFailure(Exception):
    """Public errors must never include model payloads or provider exceptions."""


class ProviderTimeout(ProviderFailure):
    pass


class InvalidProviderResponse(ProviderFailure):
    pass


class TransientProviderFailure(ProviderFailure):
    def __init__(self, *, retry_after: float | None = None):
        super().__init__("Temporary provider failure")
        self.retry_after = retry_after


class CircuitOpen(ProviderFailure):
    pass


class UsageLimitExceeded(ProviderFailure):
    def __init__(self, *, retry_after: int | None = None):
        super().__init__("AI usage limit reached")
        self.retry_after = retry_after


class UsagePolicyUnavailable(ProviderFailure):
    pass
