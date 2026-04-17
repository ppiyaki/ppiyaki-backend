package com.ppiyaki.chat;

import com.ppiyaki.common.mcp.DurMcpTools;
import com.ppiyaki.common.mcp.MedicineMcpTools;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatToolCallbackConfig {

    @Bean
    public List<ToolCallback> toolCallbacks(
            final ObjectProvider<MedicineMcpTools> medicineMcpTools,
            final ObjectProvider<DurMcpTools> durMcpTools
    ) {
        final List<ToolCallback> callbacks = new ArrayList<>();

        medicineMcpTools.ifAvailable(
                tools -> callbacks.addAll(List.of(ToolCallbacks.from(tools))));
        durMcpTools.ifAvailable(
                tools -> callbacks.addAll(List.of(ToolCallbacks.from(tools))));

        return callbacks;
    }
}
