package com.ppiyaki.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FamilySafetyScheduler {

    private static final Logger log = LoggerFactory.getLogger(FamilySafetyScheduler.class);

    private final FamilySafetyDispatcher dispatcher;

    public FamilySafetyScheduler(final FamilySafetyDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void runHourly() {
        final long start = System.currentTimeMillis();
        final int dispatched = dispatcher.run();
        final long elapsed = System.currentTimeMillis() - start;
        if (dispatched > 0) {
            log.info("FamilySafetyScheduler dispatched count={} elapsed={}ms", dispatched, elapsed);
        } else {
            log.debug("FamilySafetyScheduler tick count=0 elapsed={}ms", elapsed);
        }
    }
}
