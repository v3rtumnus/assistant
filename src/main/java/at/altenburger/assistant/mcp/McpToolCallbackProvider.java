package at.altenburger.assistant.mcp;

import at.altenburger.assistant.service.anonymization.AnonymizationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Provides Spring AI ToolCallbacks for MCP tools.
 * This allows the LLM to automatically call MCP tools via OpenAI's function calling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolCallbackProvider {

    private final McpDiscoveryService mcpDiscoveryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Thread-local to track tool calls during a request
    private static final ThreadLocal<List<ToolCall>> toolCallsTracker = ThreadLocal.withInitial(ArrayList::new);

    // Thread-local to store anonymization context for deanonymizing tool parameters
    private static final ThreadLocal<AnonymizationResult> anonymizationContext = new ThreadLocal<>();

    // Cache for tool callbacks to avoid rebuilding on every request
    private volatile List<ToolCallback> cachedCallbacks = null;
    private volatile long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

    /**
     * Clear the tool calls tracker (call at the start of a request).
     */
    public void clearToolCalls() {
        toolCallsTracker.get().clear();
    }

    /**
     * Get the list of tool calls made during the current request.
     */
    public List<ToolCall> getToolCalls() {
        return new ArrayList<>(toolCallsTracker.get());
    }

    /**
     * Set the anonymization context for the current request.
     * Tool parameters will be deanonymized before calling MCP servers.
     */
    public void setAnonymizationContext(AnonymizationResult anonymization) {
        anonymizationContext.set(anonymization);
    }

    /**
     * Clear the anonymization context (call after request completes).
     */
    public void clearAnonymizationContext() {
        anonymizationContext.remove();
    }

    /**
     * Get all MCP tools as Spring AI ToolCallbacks.
     * Uses caching to avoid rebuilding callbacks on every request.
     */
    public List<ToolCallback> getToolCallbacks() {
        long now = System.currentTimeMillis();

        // Check if cache is still valid
        if (cachedCallbacks != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
            log.debug("Using cached tool callbacks ({} tools)", cachedCallbacks.size());
            return cachedCallbacks;
        }

        // Rebuild cache
        synchronized (this) {
            // Double-check after acquiring lock
            if (cachedCallbacks != null && (now - cacheTimestamp) < CACHE_TTL_MS) {
                return cachedCallbacks;
            }

            List<ToolCallback> callbacks = new ArrayList<>();
            Map<String, List<McpClient.McpTool>> allTools = mcpDiscoveryService.getAllTools();

            for (Map.Entry<String, List<McpClient.McpTool>> entry : allTools.entrySet()) {
                String serverName = entry.getKey();

                for (McpClient.McpTool tool : entry.getValue()) {
                    ToolCallback callback = createCallback(serverName, tool);
                    callbacks.add(callback);
                    log.debug("Registered MCP tool: {}", tool.getName());
                }
            }

            cachedCallbacks = callbacks;
            cacheTimestamp = now;
            log.info("Rebuilt tool callback cache: {} MCP tools registered", callbacks.size());
            return callbacks;
        }
    }

    /**
     * Force refresh of the tool callback cache.
     * Call this when MCP server configuration changes.
     */
    public void invalidateCache() {
        synchronized (this) {
            cachedCallbacks = null;
            cacheTimestamp = 0;
            log.info("Tool callback cache invalidated");
        }
    }

    private ToolCallback createCallback(String serverName, McpClient.McpTool tool) {
        // Create a function that calls the MCP tool
        Function<McpToolRequest, String> toolFunction = request -> {
            long startTime = System.currentTimeMillis();
            try {
                log.info("Calling MCP tool: {} on {}", tool.getName(), serverName);

                McpClient client = mcpDiscoveryService.getClient(serverName);
                if (client == null) {
                    return "Error: MCP server not available: " + serverName;
                }

                // Convert request to parameters map
                Map<String, Object> params = request.toMap();

                // Deanonymize parameters before calling MCP server
                // This restores original values like "Wohnzimmer" from "[ROOM_1]"
                AnonymizationResult anonymization = anonymizationContext.get();
                if (anonymization != null && anonymization.hasAnonymizedEntities()) {
                    params = deanonymizeParams(params, anonymization);
                    log.debug("Deanonymized MCP tool parameters for {}", tool.getName());
                }

                JsonNode result = client.callTool(tool.getName(), params);

                long duration = System.currentTimeMillis() - startTime;
                log.debug("MCP tool {} returned: {} bytes in {}ms", tool.getName(), result.toString().length(), duration);

                // Track the tool call
                toolCallsTracker.get().add(new ToolCall(serverName, tool.getName(), duration));

                // Anonymize the response before returning to LLM
                // This ensures sensitive data in tool responses is also protected
                String responseStr = result.toString();
                if (anonymization != null && anonymization.hasAnonymizedEntities()) {
                    responseStr = anonymization.anonymizeWithExistingMappings(responseStr);
                    log.debug("Anonymized MCP tool response for {}", tool.getName());
                }

                return responseStr;

            } catch (Exception e) {
                log.error("Error calling MCP tool {}: {}", tool.getName(), e.getMessage());
                long duration = System.currentTimeMillis() - startTime;
                toolCallsTracker.get().add(new ToolCall(serverName, tool.getName(), duration, e.getMessage()));
                return "Error calling tool: " + e.getMessage();
            }
        };

        // Create a custom ToolCallback that uses the MCP tool's actual inputSchema
        String inputSchemaStr = tool.getInputSchema() != null
                ? tool.getInputSchema().toString()
                : "{\"type\":\"object\",\"properties\":{}}";

        return new McpToolCallback(tool.getName(), tool.getDescription(), inputSchemaStr, toolFunction);
    }

    /**
     * Deanonymize all string values in a parameter map.
     * Recursively handles nested maps and lists.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> deanonymizeParams(Map<String, Object> params, AnonymizationResult anonymization) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String strValue) {
                // Deanonymize string values
                result.put(entry.getKey(), anonymization.deanonymize(strValue));
            } else if (value instanceof Map) {
                // Recursively handle nested maps
                result.put(entry.getKey(), deanonymizeParams((Map<String, Object>) value, anonymization));
            } else if (value instanceof List<?> list) {
                // Handle lists
                List<Object> newList = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String strItem) {
                        newList.add(anonymization.deanonymize(strItem));
                    } else if (item instanceof Map) {
                        newList.add(deanonymizeParams((Map<String, Object>) item, anonymization));
                    } else {
                        newList.add(item);
                    }
                }
                result.put(entry.getKey(), newList);
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /**
     * Represents a tool call made during a request.
     */
    public static class ToolCall {
        private final String serverName;
        private final String toolName;
        private final long durationMs;
        private final String error;

        public ToolCall(String serverName, String toolName, long durationMs) {
            this(serverName, toolName, durationMs, null);
        }

        public ToolCall(String serverName, String toolName, long durationMs, String error) {
            this.serverName = serverName;
            this.toolName = toolName;
            this.durationMs = durationMs;
            this.error = error;
        }

        public String getServerName() { return serverName; }
        public String getToolName() { return toolName; }
        public long getDurationMs() { return durationMs; }
        public String getError() { return error; }
        public boolean isSuccess() { return error == null; }
    }

    /**
     * Custom ToolCallback implementation that uses the MCP tool's actual inputSchema.
     */
    private class McpToolCallback implements ToolCallback {
        private final String name;
        private final String description;
        private final String inputSchema;
        private final Function<McpToolRequest, String> toolFunction;
        private final ToolDefinition toolDefinition;

        McpToolCallback(String name, String description, String inputSchema, Function<McpToolRequest, String> toolFunction) {
            this.name = name;
            this.description = description;
            this.inputSchema = inputSchema;
            this.toolFunction = toolFunction;
            this.toolDefinition = ToolDefinition.builder()
                    .name(name)
                    .description(description != null ? description : "")
                    .inputSchema(inputSchema)
                    .build();
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return toolDefinition;
        }

        @Override
        public String call(String toolInput) {
            try {
                // Parse the JSON input into McpToolRequest
                McpToolRequest request = objectMapper.readValue(toolInput, McpToolRequest.class);
                return toolFunction.apply(request);
            } catch (Exception e) {
                log.error("Error parsing tool input for {}: {}", name, e.getMessage());
                return "Error parsing tool input: " + e.getMessage();
            }
        }
    }

    /**
     * Dynamic request class for MCP tool parameters.
     * Uses Jackson annotations to capture ANY parameter the LLM sends.
     * This allows us to support any MCP tool without hardcoding parameter names.
     */
    public static class McpToolRequest {
        private final Map<String, Object> dynamicProperties = new HashMap<>();

        /**
         * Captures any JSON property that doesn't match a specific field.
         * This is the key to supporting arbitrary tool parameters.
         */
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void setDynamicProperty(String name, Object value) {
            dynamicProperties.put(name, value);
        }

        /**
         * Returns all dynamic properties for serialization.
         */
        @com.fasterxml.jackson.annotation.JsonAnyGetter
        public Map<String, Object> getDynamicProperties() {
            return dynamicProperties;
        }

        /**
         * Convert all captured parameters to a map for the MCP call.
         */
        public Map<String, Object> toMap() {
            return new HashMap<>(dynamicProperties);
        }

        @Override
        public String toString() {
            return "McpToolRequest" + dynamicProperties;
        }
    }
}