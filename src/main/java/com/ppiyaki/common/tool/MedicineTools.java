package com.ppiyaki.common.tool;

import com.ppiyaki.medicine.controller.dto.MedicineCandidate;
import com.ppiyaki.medicine.service.MatchResult;
import com.ppiyaki.medicine.service.MedicineMatchService;
import com.ppiyaki.medicine.service.MedicineSearchService;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class MedicineTools {

    private final MedicineSearchService searchService;
    private final MedicineMatchService matchService;

    public MedicineTools(
            final MedicineSearchService searchService,
            final MedicineMatchService matchService
    ) {
        this.searchService = searchService;
        this.matchService = matchService;
    }

    @Tool(description = "Search Korean MFDS DUR drug catalog by name. Returns candidates with itemSeq, name, manufacturer, ingredients.")
    public List<MedicineCandidate> searchMedicine(
            @ToolParam(description = "Drug name or partial name to search") final String query,
            @ToolParam(description = "Max results, default 10") final Integer limit
    ) {
        final int effectiveLimit = limit != null ? limit : 10;
        return searchService.search(query, effectiveLimit);
    }

    @Tool(description = "Match OCR-extracted drug text to MFDS itemSeq with auto-correction. Returns match type (EXACT/FUZZY_AUTO/MANUAL_REQUIRED/NO_MATCH) and reason.")
    public MatchResult matchMedicineFromOcr(
            @ToolParam(description = "OCR raw text of the drug name") final String ocrText,
            @ToolParam(description = "Optional dosage hint (e.g. '500mg')") final String dosage,
            @ToolParam(description = "Optional form hint (e.g. '정', '캡슐')") final String form
    ) {
        return matchService.match(
                ocrText,
                Optional.ofNullable(dosage),
                Optional.ofNullable(form)
        );
    }
}
