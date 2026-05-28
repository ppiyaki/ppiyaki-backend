package com.ppiyaki.notification.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.exception.RetryAfterAware;

public class WellbeingPingCooldownException extends BusinessException implements RetryAfterAware {

    private final long retryAfterSeconds;

    public WellbeingPingCooldownException(final long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED,
                "Wellbeing ping cooldown active, retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
