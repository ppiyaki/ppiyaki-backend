package com.ppiyaki.medicine.controller;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.medicine.service.PillIdentificationSyncService;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 식약처 낱알식별 동기화 수동 트리거. spec §5-2 — 본 spec 한정 비공개 endpoint.
 *
 * <p>동기화는 약 10분 소요(전체 페이지 paginate)라 nginx upstream timeout(60s)을 초과한다.
 * 따라서 호출 즉시 202 Accepted 반환 후 백그라운드에서 진행. 결과는 컨테이너 로그(MDCIN_GRN sync done)로 추적.
 *
 * <p>현재는 admin role 인프라 부재로 외부에 공개되지 않는 개발자 호출 전용.
 * SecurityConfig permit 목록에 추가하지 않으면 모든 인증 사용자가 호출 가능 — prod 노출 전 권한 추가 필수.
 */
@RestController
@RequestMapping("/api/v1/admin/pill-identifications")
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class AdminPillSyncController {

    private static final Logger log = LoggerFactory.getLogger(AdminPillSyncController.class);

    private final PillIdentificationSyncService syncService;
    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "pill-sync-trigger");
        t.setDaemon(true);
        return t;
    });

    public AdminPillSyncController(final PillIdentificationSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> sync() {
        if (syncService.isInProgress()) {
            throw new BusinessException(ErrorCode.PILL_SYNC_IN_PROGRESS);
        }
        syncExecutor.submit(() -> {
            try {
                syncService.syncAll();
            } catch (final Exception e) {
                log.error("PillIdentification manual sync failed", e);
            }
        });
        return ResponseEntity.accepted().body(Map.of(
                "status", "started",
                "message", "동기화가 백그라운드에서 진행 중입니다. 컨테이너 로그(MDCIN_GRN sync done)로 결과를 확인하세요."
        ));
    }
}
