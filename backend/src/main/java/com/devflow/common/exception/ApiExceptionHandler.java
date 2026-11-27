package com.devflow.common.exception;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ErrorBody(String code, String message) {}
    public record ErrorResponse(ErrorBody error) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException exception) {
        return response(exception.status(), exception.code(), exception.getMessage());
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleImageSize() {
        return response(HttpStatus.PAYLOAD_TOO_LARGE,"INVALID_IMAGES","Image request is too large");
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter() {
        return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "조회 조건을 확인해 주세요.");
    }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleInvalidBody() {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Check request fields");
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict() {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT", "Duplicate account or invalid reference");
    }
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabase() {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "게시글을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }
    private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(new ErrorBody(code, message)));
    }
}
