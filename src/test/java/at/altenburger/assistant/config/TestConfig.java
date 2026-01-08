package at.altenburger.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class TestConfig {

    @Bean
    @Primary
    public OpenAiChatModel mockOpenAiChatModel() {
        OpenAiChatModel mockModel = mock(OpenAiChatModel.class);

        // Create a mock response
        AssistantMessage message = new AssistantMessage("This is a mock response from the AI assistant.");
        Generation generation = new Generation(message);
        ChatResponse mockResponse = new ChatResponse(java.util.List.of(generation));

        when(mockModel.call(any(Prompt.class))).thenReturn(mockResponse);

        return mockModel;
    }

    @Bean
    @Primary
    public Tracer mockTracer() {
        Tracer mockTracer = mock(Tracer.class);
        Span mockSpan = mock(Span.class);
        TraceContext mockContext = mock(TraceContext.class);
        Tracer.SpanInScope mockSpanInScope = mock(Tracer.SpanInScope.class);

        when(mockTracer.nextSpan()).thenReturn(mockSpan);
        when(mockSpan.name(anyString())).thenReturn(mockSpan);
        when(mockSpan.start()).thenReturn(mockSpan);
        when(mockSpan.context()).thenReturn(mockContext);
        when(mockContext.traceId()).thenReturn("test-trace-id");
        when(mockTracer.withSpan(any(Span.class))).thenReturn(mockSpanInScope);

        return mockTracer;
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}