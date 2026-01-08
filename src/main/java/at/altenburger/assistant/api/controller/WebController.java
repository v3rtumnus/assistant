package at.altenburger.assistant.api.controller;

import at.altenburger.assistant.mcp.McpClient;
import at.altenburger.assistant.mcp.McpDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class WebController {
    
    private final McpDiscoveryService mcpDiscoveryService;
    
    @GetMapping("/")
    public String index(Model model) {
        // Get MCP server info
        Map<String, List<McpClient.McpTool>> allTools = mcpDiscoveryService.getAllTools();
        model.addAttribute("mcpServers", allTools);
        model.addAttribute("serverCount", allTools.size());
        
        int totalTools = allTools.values().stream()
            .mapToInt(List::size)
            .sum();
        model.addAttribute("totalTools", totalTools);
        
        return "index";
    }
}
