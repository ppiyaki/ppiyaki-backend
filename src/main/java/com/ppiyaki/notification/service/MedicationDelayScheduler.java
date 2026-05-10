package com.ppiyaki.notification.service;

import com.ppiyaki.user.User;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MedicationDelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(MedicationDelayScheduler.class);

    private final MedicationDelayDispatcher dispatcher;
    private final Clock clock;

    public MedicationDelayScheduler(final MedicationDelayDispatcher dispatcher, final Clock clock) {
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void dispatchDue() {
        final LocalDate today = LocalDate.now(clock);
        int total = 0;
        for (final User senior : dispatcher.findAllSeniorsWithMealTimes()) {
            total += dispatcher.dispatchForSenior(senior, today);
        }
        if (total > 0) {
            log.info("MedicationDelayScheduler dispatched {} delay alerts at {}", total, today);
        }
    }
}
