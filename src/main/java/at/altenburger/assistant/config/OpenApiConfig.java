package at.altenburger.assistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Local AI Assistant API")
                        .version("2.0.0")
                        .description("""
                                Local AI Assistant with intelligent routing between local and cloud LLMs.
                                
                                Features:
                                - LLM-based intelligent routing (local vs cloud)
                                - MCP (Model Context Protocol) integration
                                - Multi-step planning and execution
                                - Conversation persistence with H2 database
                                - Comprehensive observability (tracing, metrics)
                                - In-memory caching for performance
                                """)
                        .contact(new Contact()
                                .name("Local AI Assistant")
                                .url("https://github.com/yourusername/local-ai-assistant"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local development server")
                ));
    }
}
