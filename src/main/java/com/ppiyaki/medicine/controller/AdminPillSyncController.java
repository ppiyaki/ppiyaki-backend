package com.ppiyaki.medicine.controller;

import com.ppiyaki.medicine.service.PillIdentificationSyncService;
import com.ppiyaki.medicine.service.PillIdentificationSyncService.SyncResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 식약처 낱알식별 동기화 수동 트리거. spec §5-2 — 본 spec 한정 비공개 endpoint.
 *
 * <p>현재는 admin role 인프라 부재로 외부에 공개되지 않는 개발자 호출 전용.
 * SecurityConfig permit 목록에 추가하지 않으면 모든 인증 사용자가 호출 가능 — prod 노출 전 권한 추가 필수.
 */
@RestController
@RequestMapping("/api/v1/admin/pill-identifications")
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class AdminPillSyncController {

    private final PillIdentificationSyncService syncService;

    public AdminPillSyncController(final PillIdentificationSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResult> sync() {
        return ResponseEntity.ok(syncService.syncAll());
    }
}
