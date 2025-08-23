package at.altenburger.assistant.config;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class LoggingMcpToolCallbackProvider extends SyncMcpToolCallbackProvider {

    public LoggingMcpToolCallbackProvider(List<McpSyncClient> clients) {
        super(clients);
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return Arrays.stream(super.getToolCallbacks())
                .map(this::wrapWithLogging)
                .toArray(ToolCallback[]::new);
    }

    private ToolCallback wrapWithLogging(ToolCallback delegate) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return delegate.getToolDefinition();
            }

            @Override
            public String call(String toolInput) {
                log.info("[MCP → Request] Tool: {}", getToolDefinition().name());
                log.info("[MCP → Input] {}", toolInput);

                String result = delegate.call(toolInput);

                log.info("[MCP ← Output] {}", result);
                return result;
            }
        };
    }
}