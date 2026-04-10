package com.ppiyaki.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(final BusinessException exception) {
        final ErrorCode errorCode = exception.getErrorCode();
        final ErrorResponse errorResponse = ErrorResponse.of(errorCode, exception.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }
}
