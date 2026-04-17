package com.ppiyaki.common.mcp;

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
public class MedicineMcpTools {

    private final MedicineSearchService searchService;
    private final MedicineMatchService matchService;

    public MedicineMcpTools(
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

    @Tool(description = "Match drug name to MFDS itemSeq. Returns match type (EXACT/CANDIDATES/NO_MATCH), recommended match, and candidates.")
    public MatchResult matchMedicineFromOcr(
            @ToolParam(description = "Drug name extracted from prescription") final String name,
            @ToolParam(description = "Optional ingredient name for fallback search") final String ingredientName
    ) {
        return matchService.match(name, Optional.ofNullable(ingredientName));
    }
}
