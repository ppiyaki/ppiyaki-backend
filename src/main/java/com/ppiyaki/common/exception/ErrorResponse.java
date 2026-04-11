package com.ppiyaki.common.exception;

public record ErrorResponse(
        boolean success,
        ErrorDetail error
) {

    public static ErrorResponse of(final ErrorCode errorCode) {
        return new ErrorResponse(false, new ErrorDetail(errorCode.getCode(),
                errorCode.getStatus().value(), errorCode.getMessage()));
    }

    public static ErrorResponse of(final ErrorCode errorCode, final String message) {
        return new ErrorResponse(false, new ErrorDetail(errorCode.getCode(),
                errorCode.getStatus().value(), message));
    }

    public record ErrorDetail(
            String code,
            int status,
            String message
    ) {
    }
}
