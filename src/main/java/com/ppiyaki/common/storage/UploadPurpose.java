package com.ppiyaki.common.storage;

public enum UploadPurpose {

    PRESCRIPTION("prescription"),
    MEDICATION_LOG("medication-log"),
    PROFILE_IMAGE("profile-image");

    private final String prefix;

    UploadPurpose(final String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }
}
