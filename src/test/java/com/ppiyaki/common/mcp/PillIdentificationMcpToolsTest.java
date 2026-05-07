package com.ppiyaki.common.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ppiyaki.common.mcp.PillIdentificationMcpTools.PillCandidate;
import com.ppiyaki.medicine.PillIdentification;
import com.ppiyaki.medicine.repository.PillIdentificationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@DisplayName("PillIdentificationMcpTools.identifyPillByAppearance")
class PillIdentificationMcpToolsTest {

    @Mock
    private PillIdentificationRepository repository;

    @InjectMocks
    private PillIdentificationMcpTools tools;

    @Test
    @DisplayName("후보 다수 반환 — PillCandidate로 변환")
    void identify_returnsCandidates() {
        final PillIdentification a = pill("ITEM-1", "타이레놀정500밀리그람");
        final PillIdentification b = pill("ITEM-2", "이부프로펜정");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b)));

        final List<PillCandidate> result = tools.identifyPillByAppearance(
                "T", null, "장방형", "하양", null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).itemSeq()).isEqualTo("ITEM-1");
        assertThat(result.get(0).itemName()).isEqualTo("타이레놀정500밀리그람");
    }

    @Test
    @DisplayName("Pageable limit=10 적용")
    void identify_limitsTo10() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        tools.identifyPillByAppearance("T", null, "원형", "하양", null);

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(repository).findAll(any(Specification.class), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
    }

    @Test
    @DisplayName("0건 → 빈 리스트 (LLM follow-up 결정 위임)")
    void identify_emptyReturnsEmpty() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        final List<PillCandidate> result = tools.identifyPillByAppearance(
                "ZZ", null, null, null, null);

        assertThat(result).isEmpty();
    }

    private PillIdentification pill(final String seq, final String name) {
        return new PillIdentification(
                seq, name, "업체", "T", null, "장방형", "하양", null, null, null,
                null, null, null, null, "https://example/img.jpg",
                null, null, "일반의약품", null, null, null, null, null,
                LocalDateTime.now());
    }
}
