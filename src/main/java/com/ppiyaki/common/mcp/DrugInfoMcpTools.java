package com.ppiyaki.common.mcp;

import com.ppiyaki.common.druginfo.DrugInfoClient;
import com.ppiyaki.common.druginfo.DrugInfoResponse;
import java.util.Optional;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mfds.api", name = "service-key")
public class DrugInfoMcpTools {

    private final DrugInfoClient drugInfoClient;

    public DrugInfoMcpTools(final DrugInfoClient drugInfoClient) {
        this.drugInfoClient = drugInfoClient;
    }

    @Tool(description = "Get detailed drug information including efficacy, side effects, usage instructions, precautions, drug interactions, storage method, and pill image URL. Uses Korean e약은요 public API.")
    public DrugInfoResponse getDrugInfo(
            @ToolParam(description = "Drug name to search (Korean)") final String itemName
    ) {
        final Optional<DrugInfoResponse> result = drugInfoClient.search(itemName);
        return result.orElse(new DrugInfoResponse(
                itemName, null, null, null, null, null, null, null, null));
    }
}
