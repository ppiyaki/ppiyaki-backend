package com.ppiyaki.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "Invalid input"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", "Malformed request"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_003", "Internal server error"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_004", "Access denied"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_005", "Resource not found"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "COMMON_006", "Too many requests"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_007", "Method not allowed"),

    // Auth
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_001", "Invalid token"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_002", "Token expired"),
    AUTH_DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "AUTH_003", "Login ID already exists"),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_004", "Invalid login ID or password"),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "User not found"),
    MEAL_TIMES_NOT_SET(HttpStatus.BAD_REQUEST, "USER_002", "Meal times are not set for the senior"),

    // Medicine
    MEDICINE_NOT_FOUND(HttpStatus.NOT_FOUND, "MEDICINE_001", "Medicine not found"),
    PILL_SYNC_IN_PROGRESS(HttpStatus.CONFLICT, "MEDICINE_002",
            "Pill identification sync already in progress"),

    // Medication Schedule
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_001", "Schedule not found"),
    SCHEDULE_MEDICINE_MISMATCH(HttpStatus.BAD_REQUEST, "SCHEDULE_002", "Schedule does not belong to this medicine"),

    // DUR
    DUR_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "DUR_001", "DUR service unavailable"),

    // Care Relation
    CARE_RELATION_NOT_FOUND(HttpStatus.FORBIDDEN, "CARE_001", "No active care relation"),
    CARE_RELATION_REQUIRED(HttpStatus.FORBIDDEN, "CARE_002", "Caregiver must specify seniorId"),
    CARE_RELATION_NOT_CAREGIVER(HttpStatus.FORBIDDEN, "CARE_003", "Only caregivers can specify seniorId"),
    CARE_MODE_RESTRICTED(HttpStatus.FORBIDDEN, "CARE_004",
            "Senior cannot mutate prescription before caregiver review window"),
    CARE_RELATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "CARE_007", "Care relation already exists"),
    CARE_RELATION_ROLE_MISMATCH(HttpStatus.FORBIDDEN, "CARE_008", "Role does not match the required action"),
    CARE_RELATION_INVITE_INVALID(HttpStatus.UNAUTHORIZED, "CARE_009", "Invalid invite code"),

    // Pet
    PET_NOT_FOUND(HttpStatus.NOT_FOUND, "PET_001", "Pet not found"),

    // Chat
    CHAT_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_001", "Chat session not found"),
    CHAT_SESSION_EXPIRED(HttpStatus.GONE, "CHAT_002", "Chat session expired"),
    CHAT_SESSION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CHAT_003", "Chat session access denied"),
    CHAT_VOICE_FILE_EMPTY(HttpStatus.BAD_REQUEST, "CHAT_004", "Voice file is empty"),
    CHAT_PHOTO_FILE_EMPTY(HttpStatus.BAD_REQUEST, "CHAT_005", "Photo file is empty"),
    CHAT_PHOTO_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "CHAT_006",
            "Photo file type not supported (jpeg/png/webp only)"),
    CHAT_PHOTO_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "CHAT_007", "Photo file exceeds 10MB limit"),

    // Notification
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_001", "Notification not found"),
    NOTIFICATION_FORBIDDEN(HttpStatus.FORBIDDEN, "NOTIFICATION_002", "Cannot access another user's notification"),
    DEVICE_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_003", "Device token not found"),
    DEVICE_TOKEN_FORBIDDEN(HttpStatus.FORBIDDEN, "NOTIFICATION_004", "Cannot access another user's device token");

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
