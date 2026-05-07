package com.ppiyaki.common.mcp;

import com.ppiyaki.common.exception.BusinessException;
import com.ppiyaki.common.exception.ErrorCode;
import com.ppiyaki.health.controller.dto.DurCheckResponse;
import com.ppiyaki.health.service.DurCheckService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class DurMcpTools {

    private final DurCheckService durCheckService;

    public DurMcpTools(final DurCheckService durCheckService) {
        this.durCheckService = durCheckService;
    }

    @Tool(description = "Run a DUR (Drug Utilization Review) safety check on a medicine. Checks drug interactions, elderly warnings, and therapeutic duplicates against the senior's active medications.")
    public DurCheckResponse checkDur(
            @ToolParam(description = "Medicine ID to check") final Long medicineId,
            @ToolParam(description = "Bypass 24h cache if true") final Boolean forceRefresh,
            final ToolContext toolContext
    ) {
        final Long userId = resolveUserId(toolContext);
        final boolean refresh = forceRefresh != null && forceRefresh;
        return durCheckService.check(userId, medicineId, refresh);
    }

    /**
     * Spring AI 도구 호출 thread는 Reactor BoundedElastic이라 SecurityContextHolder가 비어있다.
     * ChatSessionService가 .toolContext(Map.of("userId", userId))로 전달한 값을 사용한다.
     */
    private static Long resolveUserId(final ToolContext toolContext) {
        if (toolContext == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "ToolContext is missing userId");
        }
        final Object value = toolContext.getContext().get("userId");
        if (!(value instanceof Long userId)) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "ToolContext userId is missing or not a Long");
        }
        return userId;
    }
}
