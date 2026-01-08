package at.altenburger.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "assistant.routing")
public class RoutingConfig {

    private String defaultProvider = "local"; // local or cloud
}
