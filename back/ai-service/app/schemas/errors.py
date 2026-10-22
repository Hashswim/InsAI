from enum import Enum
from typing import Literal
from uuid import UUID

from pydantic import BaseModel


class ErrorCode(str, Enum):
    INVALID_REQUEST_ID = "INVALID_REQUEST_ID"
    REQUEST_ID_MISMATCH = "REQUEST_ID_MISMATCH"
    INVALID_INPUT = "INVALID_INPUT"
    PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE"
    UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE"
    UNAUTHORIZED = "UNAUTHORIZED"
    FORBIDDEN = "FORBIDDEN"
    NOT_FOUND = "NOT_FOUND"
    METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED"
    SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"
    AI_ANALYSIS_FAILED = "AI_ANALYSIS_FAILED"
    INVALID_AI_RESPONSE = "INVALID_AI_RESPONSE"
    AI_TIMEOUT = "AI_TIMEOUT"
    RATE_LIMITED = "RATE_LIMITED"
    INTERNAL_ERROR = "INTERNAL_ERROR"


class ErrorDetail(BaseModel):
    code: ErrorCode
    message: str


class ErrorResponse(BaseModel):
    success: Literal[False] = False
    request_id: UUID
    error: ErrorDetail
