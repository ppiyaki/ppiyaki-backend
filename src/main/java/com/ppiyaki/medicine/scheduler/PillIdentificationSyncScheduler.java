package com.ppiyaki.medicine.scheduler;

import com.ppiyaki.medicine.service.PillIdentificationSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주 1회 (KST 일요일 02:00) 식약처 낱알식별 데이터 동기화.
 * spec docs/features/pill-identification.md §3 (cron) / §5-4.
 */
@Component
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class PillIdentificationSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(PillIdentificationSyncScheduler.class);

    private final PillIdentificationSyncService syncService;

    public PillIdentificationSyncScheduler(final PillIdentificationSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(cron = "0 0 2 ? * SUN", zone = "Asia/Seoul")
    public void runWeeklySync() {
        try {
            syncService.syncAll();
        } catch (final Exception e) {
            log.error("PillIdentification weekly sync failed", e);
        }
    }
}
