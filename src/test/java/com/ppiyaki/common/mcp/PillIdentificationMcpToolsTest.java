package com.ppiyaki.common.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ppiyaki.common.mcp.PillIdentificationMcpTools.PillIdentifyResult;
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
import org.springframework.data.domain.PageRequest;
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
    @DisplayName("totalMatches == candidates 수 → 후보 그대로 반환")
    void identify_returnsCandidatesWithTotal() {
        final PillIdentification a = pill("ITEM-1", "타이레놀정500밀리그람");
        final PillIdentification b = pill("ITEM-2", "이부프로펜정");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(0, 10), 2));

        final PillIdentifyResult result = tools.identifyPillByAppearance(
                "T", null, "장방형", "하양", null);

        assertThat(result.totalMatches()).isEqualTo(2);
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates().get(0).itemName()).isEqualTo("타이레놀정500밀리그람");
    }

    @Test
    @DisplayName("totalMatches > candidates 수 (잘림) → totalMatches로 follow-up 신호 전달")
    void identify_truncated_signalsTotalMatches() {
        final PillIdentification a = pill("ITEM-1", "장방형하양약A");
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a), PageRequest.of(0, 10), 1866));

        final PillIdentifyResult result = tools.identifyPillByAppearance(
                null, null, "장방형", "하양", null);

        assertThat(result.totalMatches()).isEqualTo(1866);
        assertThat(result.candidates()).hasSize(1);
        // LLM은 totalMatches > candidates.size()로 truncation 인지 → 사용자에게 follow-up
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
    @DisplayName("0건 → totalMatches=0, candidates=[]")
    void identify_emptyReturnsZero() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        final PillIdentifyResult result = tools.identifyPillByAppearance(
                "ZZ", null, null, null, null);

        assertThat(result.totalMatches()).isEqualTo(0);
        assertThat(result.candidates()).isEmpty();
    }

    private PillIdentification pill(final String seq, final String name) {
        return new PillIdentification(
                seq, name, "업체", "T", null, "장방형", "하양", null, null, null,
                null, null, null, null, "https://example/img.jpg",
                null, null, "일반의약품", null, null, null, null, null,
                LocalDateTime.now());
    }
}
