package at.altenburger.assistant.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;
import java.util.Map;

public interface McpClient {
    
    /**
     * Initialize the MCP server connection
     */
    void initialize() throws Exception;
    
    /**
     * List all available tools from this MCP server
     */
    List<McpTool> listTools() throws Exception;
    
    /**
     * Call a tool with given parameters
     */
    JsonNode callTool(String toolName, Map<String, Object> parameters) throws Exception;
    
    /**
     * Check if the client is connected
     */
    boolean isConnected();
    
    /**
     * Close the connection
     */
    void close();

    @Data
    class McpTool {
        private String name;
        private String description;
        private JsonNode inputSchema;
    }
}
