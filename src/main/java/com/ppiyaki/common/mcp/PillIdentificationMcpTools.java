package com.ppiyaki.common.mcp;

import com.ppiyaki.medicine.PillIdentification;
import com.ppiyaki.medicine.repository.PillIdentificationRepository;
import com.ppiyaki.medicine.repository.PillIdentificationSpecifications;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * 알약 외형(각인·색·모양·분할선) 기반 식별 도구.
 * spec docs/features/pill-identification.md.
 *
 * <p>vision LLM이 사진에서 약명을 추정 못 할 때, 외형 묘사만 추출해 본 도구를 호출하면
 * pill_identifications 자체 인덱스에서 후보 약을 반환한다.
 * 약명 추정 의존을 제거해 chat photo 흐름의 식별 갭을 메운다.
 */
@Component
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class PillIdentificationMcpTools {

    private static final Logger log = LoggerFactory.getLogger(PillIdentificationMcpTools.class);

    private static final int LIMIT = 10;

    private final PillIdentificationRepository repository;

    public PillIdentificationMcpTools(final PillIdentificationRepository repository) {
        this.repository = repository;
    }

    @Tool(description = """
            Identify a pill by its physical appearance — imprint, color, shape, line. \
            Use this when the user sends a photo and you can extract the pill's visual features \
            but cannot determine the drug name from the image alone. \
            All parameters are optional — the more provided, the narrower the candidate set. \
            Returns totalMatches (전체 매칭 수) + up to 10 candidates. \
            If totalMatches > candidates.size(), the result was truncated — ask the user for more detail \
            (clearer imprint photo or spoken letters).""")
    public PillIdentifyResult identifyPillByAppearance(
            @ToolParam(description = "Front imprint text/symbol (예: 'T', 'AT500'). null if not visible.") final String printFront,
            @ToolParam(description = "Back imprint. null if not visible.") final String printBack,
            @ToolParam(description = "Shape (예: '원형', '장방형', '타원형', '삼각형', '사각형', '마름모형', '반원형'). null if uncertain.") final String drugShape,
            @ToolParam(description = "Primary color (예: '하양', '노랑', '빨강', '파랑', '초록', '주황', '분홍', '자주', '갈색', '검정'). null if uncertain.") final String colorClass1,
            @ToolParam(description = "Front split line ('+형', '-형'). null if none.") final String lineFront
    ) {
        log.info("identifyPillByAppearance called: printFront={} printBack={} drugShape={} colorClass1={} lineFront={}",
                printFront, printBack, drugShape, colorClass1, lineFront);
        final Page<PillIdentification> page = repository.findAll(
                PillIdentificationSpecifications.byAppearance(
                        printFront, printBack, drugShape, colorClass1, lineFront),
                PageRequest.of(0, LIMIT)
        );
        final List<PillCandidate> candidates = page.getContent().stream()
                .map(PillCandidate::from).toList();
        log.info("identifyPillByAppearance result: totalMatches={} candidatesReturned={}",
                page.getTotalElements(), candidates.size());
        return new PillIdentifyResult(page.getTotalElements(), candidates);
    }

    public record PillIdentifyResult(
            long totalMatches,
            List<PillCandidate> candidates
    ) {
    }

    public record PillCandidate(
            String itemSeq,
            String itemName,
            String entpName,
            String drugShape,
            String colorClass1,
            String printFront,
            String printBack,
            String lineFront,
            String etcOtcName,
            String itemImage
    ) {
        public static PillCandidate from(final PillIdentification pill) {
            return new PillCandidate(
                    pill.getItemSeq(),
                    pill.getItemName(),
                    pill.getEntpName(),
                    pill.getDrugShape(),
                    pill.getColorClass1(),
                    pill.getPrintFront(),
                    pill.getPrintBack(),
                    pill.getLineFront(),
                    pill.getEtcOtcName(),
                    pill.getItemImage()
            );
        }
    }
}
