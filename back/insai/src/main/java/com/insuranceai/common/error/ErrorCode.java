package com.insuranceai.common.error;

import org.springframework.http.HttpStatus;

/**
 * 도메인 공통 에러 코드. 기능이 늘어나면 여기에 항목을 추가한다.
 */
public enum ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없습니다."),
	CONFLICT(HttpStatus.CONFLICT, "현재 상태에서 처리할 수 없습니다."),
	PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "요청 크기가 제한을 초과했습니다."),
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 메서드입니다."),
	AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 분석 서비스를 사용할 수 없습니다."),
	AI_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "AI 분석에 실패했습니다."),
	AI_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "AI 분석 응답이 올바르지 않습니다."),
	AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 분석 응답 시간이 초과되었습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultMessage() {
		return defaultMessage;
	}
}
