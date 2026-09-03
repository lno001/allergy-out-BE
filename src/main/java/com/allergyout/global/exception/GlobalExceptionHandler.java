package com.allergyout.global.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.allergyout.global.common.ApiResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	// 서비스 코드에서 직접 던지는 예외 (CustomException(ErrorCode.XXX))
	@ExceptionHandler(CustomException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleCustomException(CustomException e) {
    ErrorCode errorCode = e.getErrorCode();
    log.warn("CustomException: {}", e.getMessage());
    return ResponseEntity.status(errorCode.getStatus())
            .body(ApiResponse.fail(errorCode.getStatus().value(),
                                   errorCode.getMessage(),
                                   e.getDetails()));
}

	// @Valid 검증 실패 (요청 DTO의 @NotBlank 등)
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException e) {
		Map<String, String> details = new LinkedHashMap<>();
		for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
			details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}
		log.warn("Validation failed: {}", details);
		return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                         .body(ApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE.getStatus().value(), 
                                                ErrorCode.INVALID_INPUT_VALUE.getMessage(), 
                                                details));
	}

	// 경로·쿼리 파라미터 타입 불일치 (예: GET /api/recipes/abc — recipeNo 가 숫자 아님) → 400
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		log.warn("Type mismatch: {} = {}", e.getName(), e.getValue());
		return ResponseEntity.status(ErrorCode.INVALID_INPUT_VALUE.getStatus())
                         .body(ApiResponse.fail(ErrorCode.INVALID_INPUT_VALUE.getStatus().value(),
                                                ErrorCode.INVALID_INPUT_VALUE.getMessage()));
	}

	// 예상 못한 나머지 전부
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
		log.error("Unhandled exception", e);
		return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                         .body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR.getStatus().value(), 
                                                ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
	}
}
