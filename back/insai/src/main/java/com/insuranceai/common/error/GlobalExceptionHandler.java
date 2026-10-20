package com.insuranceai.common.error;

import com.insuranceai.common.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		ErrorCode code = e.getErrorCode();
		return ResponseEntity.status(code.status())
				.body(ApiResponse.fail(code.name(), e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		String detail = e.getBindingResult().getFieldErrors().stream()
				.map(this::formatFieldError)
				.collect(Collectors.joining(", "));
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.status())
				.body(ApiResponse.fail(ErrorCode.INVALID_INPUT.name(), detail));
	}

	@ExceptionHandler({
			MissingRequestHeaderException.class,
			MethodArgumentTypeMismatchException.class,
			HttpMessageNotReadableException.class
	})
	public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception e) {
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.status())
				.body(ApiResponse.fail(ErrorCode.INVALID_INPUT.name(), ErrorCode.INVALID_INPUT.defaultMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		log.error("Unhandled exception", e);
		return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
				.body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.name(), ErrorCode.INTERNAL_ERROR.defaultMessage()));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException e) {
		return ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.status()).body(ApiResponse.fail(
				ErrorCode.UNSUPPORTED_MEDIA_TYPE.name(), ErrorCode.UNSUPPORTED_MEDIA_TYPE.defaultMessage()));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException e) {
		var response = ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.status());
		if (e.getSupportedMethods() != null) response.header("Allow", String.join(", ", e.getSupportedMethods()));
		return response.body(ApiResponse.fail(ErrorCode.METHOD_NOT_ALLOWED.name(),
				ErrorCode.METHOD_NOT_ALLOWED.defaultMessage()));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
		return ResponseEntity.status(ErrorCode.NOT_FOUND.status()).body(ApiResponse.fail(
				ErrorCode.NOT_FOUND.name(), ErrorCode.NOT_FOUND.defaultMessage()));
	}

	private String formatFieldError(FieldError fe) {
		return fe.getField() + ": " + fe.getDefaultMessage();
	}
}
