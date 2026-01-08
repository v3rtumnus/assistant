package at.altenburger.assistant.mcp;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
@DisplayName("StdioMcpClient Tests")
class StdioMcpClientTest {
    
    @Mock
    private Tracer tracer;
    
    private StdioMcpClient mcpClient;
    
    @BeforeEach
    void setUp() {
        mcpClient = new StdioMcpClient(
                "test-server",
                "echo",
                List.of("hello"),
                Map.of(),
                tracer
        );
    }
    
    @Test
    @DisplayName("Should not be connected initially")
    void shouldNotBeConnectedInitially() {
        assertThat(mcpClient.isConnected()).isFalse();
    }
    
    @Test
    @DisplayName("Should throw exception when calling tool before initialization")
    void shouldThrowExceptionWhenCallingToolBeforeInit() {
        assertThatThrownBy(() -> mcpClient.callTool("test-tool", Map.of()))
                .isInstanceOf(Exception.class);
    }
}
