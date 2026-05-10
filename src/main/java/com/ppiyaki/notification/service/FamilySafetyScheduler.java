package com.ppiyaki.notification.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FamilySafetyScheduler {

    private final FamilySafetyDispatcher dispatcher;

    public FamilySafetyScheduler(final FamilySafetyDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void runHourly() {
        dispatcher.run();
    }
}
