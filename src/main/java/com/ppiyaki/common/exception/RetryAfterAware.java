package com.ppiyaki.common.exception;

public interface RetryAfterAware {

    long getRetryAfterSeconds();
}
