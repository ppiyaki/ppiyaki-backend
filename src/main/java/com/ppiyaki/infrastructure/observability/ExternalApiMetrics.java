package com.ppiyaki.infrastructure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 외부 API 호출(MFDS, DUR, DrugInfo, Clova OCR 등) 메트릭 기록 헬퍼.
 * 카운터 + 타이머를 한 호출로 묶어 라벨 일관성 강제.
 */
public final class ExternalApiMetrics {

    public static final String COUNTER_NAME = "ppiyaki.external.api.total";
    public static final String TIMER_NAME = "ppiyaki.external.api.seconds";
    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILED = "failed";

    private ExternalApiMetrics() {
    }

    public static void record(
            final MeterRegistry registry,
            final String api,
            final String operation,
            final String result,
            final Timer.Sample sample
    ) {
        registry.counter(COUNTER_NAME,
                "api", api, "operation", operation, "result", result).increment();
        sample.stop(registry.timer(TIMER_NAME,
                "api", api, "operation", operation, "result", result));
    }
}
