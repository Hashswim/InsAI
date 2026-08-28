package com.insuranceai.common.web;

/**
 * 모든 REST 응답의 공통 봉투(envelope).
 *
 * <pre>
 * 성공: { "success": true,  "data": ..., "error": null }
 * 실패: { "success": false, "data": null, "error": { "code": "...", "message": "..." } }
 * </pre>
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<Void> ok() {
		return new ApiResponse<>(true, null, null);
	}

	public static ApiResponse<Void> fail(String code, String message) {
		return new ApiResponse<>(false, null, new ErrorBody(code, message));
	}

	public record ErrorBody(String code, String message) {
	}
}
