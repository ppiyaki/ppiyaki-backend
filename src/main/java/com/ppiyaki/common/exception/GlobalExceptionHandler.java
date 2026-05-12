package com.ppiyaki.common.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(final BusinessException exception) {
        final ErrorCode errorCode = exception.getErrorCode();
        log.warn("BusinessException: {} - {}", errorCode.getCode(), exception.getMessage());
        final ErrorResponse errorResponse = errorCode == ErrorCode.INTERNAL_SERVER_ERROR
                ? ErrorResponse.of(errorCode)
                : ErrorResponse.of(errorCode, exception.getMessage());
        return ResponseEntity.status(errorCode.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(final MethodArgumentNotValidException exception) {
        final String message = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.debug("Validation failed: {}", message);
        final ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.INVALID_INPUT,
                message.isBlank() ? ErrorCode.INVALID_INPUT.getMessage() : message);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(final ConstraintViolationException exception) {
        final String message = exception.getConstraintViolations().stream()
                .map(violation -> leafProperty(violation.getPropertyPath().toString()) + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        log.debug("Constraint violation: {}", message);
        final ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.INVALID_INPUT,
                message.isBlank() ? ErrorCode.INVALID_INPUT.getMessage() : message);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus()).body(errorResponse);
    }

    private String leafProperty(final String propertyPath) {
        final int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMalformedRequest(final Exception exception) {
        log.debug("Malformed request: {}", exception.getMessage());
        final ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.MALFORMED_REQUEST);
        return ResponseEntity.status(ErrorCode.MALFORMED_REQUEST.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(final NoHandlerFoundException exception) {
        log.debug("No handler found: {}", exception.getMessage());
        final ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.NOT_FOUND,
                "No endpoint " + exception.getHttpMethod() + " " + exception.getRequestURL());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(final NoResourceFoundException exception) {
        log.debug("No resource found: {} {}", exception.getHttpMethod(), exception.getResourcePath());
        final ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.NOT_FOUND,
                "No endpoint " + exception.getHttpMethod() + " /" + exception.getResourcePath());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(final AccessDeniedException exception) {
        log.debug("Access denied: {}", exception.getMessage());
        final ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.FORBIDDEN);
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus()).body(errorResponse);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            final HttpRequestMethodNotSupportedException exception) {
        log.debug("Method not allowed: {}", exception.getMessage());
        final ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED,
                "Method " + exception.getMethod() + " not allowed");
        final ResponseEntity.BodyBuilder builder = ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus());
        final Set<HttpMethod> supportedMethods = exception.getSupportedHttpMethods();
        if (supportedMethods != null && !supportedMethods.isEmpty()) {
            builder.allow(supportedMethods.toArray(new HttpMethod[0]));
        }
        return builder.body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(final Exception exception) {
        log.error("Unhandled exception", exception);
        final ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus()).body(errorResponse);
    }
}
