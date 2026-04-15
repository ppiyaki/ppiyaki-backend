package com.ppiyaki.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "Invalid input"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", "Malformed request"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_003", "Internal server error"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_004", "Access denied"),

    // Auth
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_001", "Invalid token"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_002", "Token expired"),
    AUTH_DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "AUTH_003", "Login ID already exists"),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_004", "Invalid login ID or password"),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "User not found"),

    // Medicine
    MEDICINE_NOT_FOUND(HttpStatus.NOT_FOUND, "MEDICINE_001", "Medicine not found"),

    // Medication Schedule
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_001", "Schedule not found"),
    SCHEDULE_MEDICINE_MISMATCH(HttpStatus.BAD_REQUEST, "SCHEDULE_002", "Schedule does not belong to this medicine"),

    // Care Relation
    CARE_RELATION_NOT_FOUND(HttpStatus.FORBIDDEN, "CARE_001", "No active care relation"),
    CARE_RELATION_REQUIRED(HttpStatus.FORBIDDEN, "CARE_002", "Caregiver must specify seniorId"),
    CARE_RELATION_NOT_CAREGIVER(HttpStatus.FORBIDDEN, "CARE_003", "Only caregivers can specify seniorId");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(final HttpStatus status, final String code, final String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
