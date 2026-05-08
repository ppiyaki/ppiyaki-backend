package com.ppiyaki.medicine.service;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.common.mfds.MdcinGrnIdntfcInfoClient;
import com.ppiyaki.common.mfds.MdcinGrnIdntfcInfoClient.PillItem;
import com.ppiyaki.common.mfds.MdcinGrnIdntfcInfoClient.PillPage;
import com.ppiyaki.medicine.PillIdentification;
import com.ppiyaki.medicine.repository.PillIdentificationRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 식약처 의약품 낱알식별 정보 일괄 동기화 서비스.
 * spec docs/features/pill-identification.md §5-4 (동기화 batch).
 *
 * <p>전체 페이지를 paginate하며 item_seq 기준 idempotent upsert.
 * 부분 실패 시 다음 cron 또는 수동 트리거로 복구 가능.
 */
@Service
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class PillIdentificationSyncService {

    private static final Logger log = LoggerFactory.getLogger(PillIdentificationSyncService.class);

    private final MdcinGrnIdntfcInfoClient client;
    private final PillIdentificationRepository repository;

    /**
     * 동시 동기화 방지 lock. 첫 호출이 끝날 때까지 후속 호출은 SYNC_IN_PROGRESS 거절.
     * curl/nginx retry로 인한 동시 PK 충돌 방지 (PoC에서 발견된 갭).
     */
    private final AtomicBoolean inProgress = new AtomicBoolean(false);

    public PillIdentificationSyncService(
            final MdcinGrnIdntfcInfoClient client,
            final PillIdentificationRepository repository
    ) {
        this.client = client;
        this.repository = repository;
    }

    public boolean isInProgress() {
        return inProgress.get();
    }

    public SyncResult syncAll() {
        if (!inProgress.compareAndSet(false, true)) {
            throw new BusinessException(ErrorCode.PILL_SYNC_IN_PROGRESS);
        }
        try {
            return doSyncAll();
        } finally {
            inProgress.set(false);
        }
    }

    private SyncResult doSyncAll() {
        final long startTime = System.currentTimeMillis();
        int pageNo = 1;
        int upserted = 0;
        int totalCount = -1;

        while (true) {
            final PillPage page = client.fetchPage(pageNo, MdcinGrnIdntfcInfoClient.DEFAULT_NUM_OF_ROWS);
            if (totalCount < 0) {
                totalCount = page.totalCount();
                log.info("MDCIN_GRN sync started: totalCount={}", totalCount);
            }
            if (page.items().isEmpty()) {
                break;
            }
            for (final PillItem item : page.items()) {
                if (item.itemSeq() == null || item.itemName() == null) {
                    continue;
                }
                upsert(item);
                upserted++;
            }
            if (upserted >= totalCount) {
                break;
            }
            pageNo++;
        }

        final long elapsed = System.currentTimeMillis() - startTime;
        log.info("MDCIN_GRN sync done: upserted={} totalCount={} elapsed={}ms",
                upserted, totalCount, elapsed);
        return new SyncResult(upserted, totalCount, elapsed);
    }

    @Transactional
    protected void upsert(final PillItem item) {
        final LocalDateTime now = LocalDateTime.now();
        final PillIdentification fresh = new PillIdentification(
                item.itemSeq(), item.itemName(), item.entpName(),
                item.printFront(), item.printBack(),
                item.drugShape(), item.colorClass1(), item.colorClass2(),
                item.lineFront(), item.lineBack(),
                item.lengLong(), item.lengShort(), item.thick(),
                item.chart(), item.itemImage(),
                item.classNo(), item.className(), item.etcOtcName(),
                item.markCodeFront(), item.markCodeBack(),
                item.ediCode(), item.bizrno(), item.changeDate(),
                now
        );
        final Optional<PillIdentification> existing = repository.findById(item.itemSeq());
        if (existing.isPresent()) {
            existing.get().updateFromSync(fresh);
        } else {
            repository.save(fresh);
        }
    }

    public record SyncResult(int upserted, int totalCount, long elapsedMs) {
    }
}
