package com.ppiyaki.medicine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ppiyaki.infrastructure.mfds.MdcinGrnIdntfcInfoClient;
import com.ppiyaki.infrastructure.mfds.MdcinGrnIdntfcInfoClient.PillItem;
import com.ppiyaki.infrastructure.mfds.MdcinGrnIdntfcInfoClient.PillPage;
import com.ppiyaki.medicine.PillIdentification;
import com.ppiyaki.medicine.repository.PillIdentificationRepository;
import com.ppiyaki.medicine.service.PillIdentificationSyncService.SyncResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PillIdentificationSyncService")
class PillIdentificationSyncServiceTest {

    @Mock
    private MdcinGrnIdntfcInfoClient client;
    @Mock
    private PillIdentificationRepository repository;

    @InjectMocks
    private PillIdentificationSyncService service;

    @BeforeEach
    void setUp() {
        // findById 기본 empty (신규 INSERT 경로). lenient — 모든 테스트에서 호출되지는 않음.
        lenient().when(repository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("두 페이지에 걸친 데이터 → 모두 upsert (신규 INSERT)")
    void syncAll_twoPages_inserts() {
        final PillItem a = item("ITEM-1", "타이레놀정");
        final PillItem b = item("ITEM-2", "이부프로펜정");
        final PillItem c = item("ITEM-3", "아스피린정");
        when(client.fetchPage(eq(1), anyInt())).thenReturn(new PillPage(3, List.of(a, b)));
        when(client.fetchPage(eq(2), anyInt())).thenReturn(new PillPage(3, List.of(c)));

        final SyncResult result = service.syncAll();

        assertThat(result.upserted()).isEqualTo(3);
        assertThat(result.totalCount()).isEqualTo(3);

        final ArgumentCaptor<PillIdentification> captor = ArgumentCaptor.forClass(PillIdentification.class);
        verify(repository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(PillIdentification::getItemSeq)
                .containsExactly("ITEM-1", "ITEM-2", "ITEM-3");
    }

    @Test
    @DisplayName("기존 row 있으면 update 경로 (save 미호출)")
    void syncAll_existing_updates() {
        final PillItem a = item("ITEM-1", "타이레놀정");
        when(client.fetchPage(eq(1), anyInt())).thenReturn(new PillPage(1, List.of(a)));

        final PillIdentification existing = pill("ITEM-1", "old name");
        when(repository.findById("ITEM-1")).thenReturn(Optional.of(existing));

        final SyncResult result = service.syncAll();

        assertThat(result.upserted()).isEqualTo(1);
        // save 호출 안 됨 — JPA dirty checking으로 update
        verify(repository, times(0)).save(any());
        assertThat(existing.getItemName()).isEqualTo("타이레놀정");
    }

    @Test
    @DisplayName("itemSeq 또는 itemName이 null인 row는 skip")
    void syncAll_skipsInvalid() {
        final PillItem valid = item("ITEM-1", "정상");
        final PillItem nullSeq = new PillItem(
                null, "약명", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        final PillItem nullName = new PillItem(
                "ITEM-X", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        // totalCount는 valid 1건 기준 — invalid skip 후 loop 종료 보장
        when(client.fetchPage(eq(1), anyInt())).thenReturn(new PillPage(1, List.of(valid, nullSeq, nullName)));

        final SyncResult result = service.syncAll();

        assertThat(result.upserted()).isEqualTo(1);
        verify(repository, times(1)).save(any());
    }

    @Test
    @DisplayName("빈 페이지 응답이면 즉시 종료")
    void syncAll_emptyPage_breaks() {
        when(client.fetchPage(eq(1), anyInt())).thenReturn(new PillPage(0, List.of()));

        final SyncResult result = service.syncAll();

        assertThat(result.upserted()).isEqualTo(0);
        verify(repository, times(0)).save(any());
        verify(client, times(1)).fetchPage(anyInt(), anyInt());
    }

    @Test
    @DisplayName("동시 호출 — 두 번째는 PILL_SYNC_IN_PROGRESS")
    void syncAll_concurrent_secondCall_throws() throws Exception {
        // 첫 호출이 끝나기 전에 두 번째가 들어오면 거절돼야.
        // 첫 호출의 fetchPage가 latch로 막혀있는 동안 두 번째가 시도.
        final java.util.concurrent.CountDownLatch firstStarted = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch firstHold = new java.util.concurrent.CountDownLatch(1);
        when(client.fetchPage(eq(1), anyInt())).thenAnswer(inv -> {
            firstStarted.countDown();
            firstHold.await();
            return new PillPage(0, List.of());
        });

        final java.util.concurrent.atomic.AtomicReference<Throwable> secondError = new java.util.concurrent.atomic.AtomicReference<>();
        final Thread first = new Thread(service::syncAll);
        first.start();
        firstStarted.await();

        final Thread second = new Thread(() -> {
            try {
                service.syncAll();
            } catch (final Throwable e) {
                secondError.set(e);
            }
        });
        second.start();
        second.join(2000);

        // 첫 동기화 풀어줌
        firstHold.countDown();
        first.join(2000);

        assertThat(secondError.get())
                .isInstanceOf(com.ppiyaki.common.exception.BusinessException.class)
                .extracting(e -> ((com.ppiyaki.common.exception.BusinessException) e).getErrorCode())
                .isEqualTo(com.ppiyaki.common.exception.ErrorCode.PILL_SYNC_IN_PROGRESS);
        assertThat(service.isInProgress()).isFalse();
    }

    private PillItem item(final String seq, final String name) {
        return new PillItem(
                seq, name, "업체", "T", null, "원형", "하양", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private PillIdentification pill(final String seq, final String name) {
        return new PillIdentification(
                seq, name, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                java.time.LocalDateTime.now());
    }
}
