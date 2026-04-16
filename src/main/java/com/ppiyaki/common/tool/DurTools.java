package com.ppiyaki.common.tool;

import com.ppiyaki.health.controller.dto.DurCheckResponse;
import com.ppiyaki.health.service.DurCheckService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class DurTools {

    private final DurCheckService durCheckService;

    public DurTools(final DurCheckService durCheckService) {
        this.durCheckService = durCheckService;
    }

    @Tool(description = "Run a DUR (Drug Utilization Review) safety check on a medicine. Checks drug interactions, elderly warnings, and therapeutic duplicates against the senior's active medications.")
    public DurCheckResponse checkDur(
            @ToolParam(description = "Medicine ID to check") final Long medicineId,
            @ToolParam(description = "Bypass 24h cache if true") final Boolean forceRefresh
    ) {
        final Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        final boolean refresh = forceRefresh != null && forceRefresh;
        return durCheckService.check(userId, medicineId, refresh);
    }
}
