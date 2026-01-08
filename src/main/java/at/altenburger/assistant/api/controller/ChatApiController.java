package at.altenburger.assistant.api.controller;

import at.altenburger.assistant.api.dto.ChatRequest;
import at.altenburger.assistant.api.dto.ChatResponse;
import at.altenburger.assistant.core.service.ConversationService;
import at.altenburger.assistant.domain.entity.ConversationEntity;
import at.altenburger.assistant.service.AiOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Chat API for interacting with the AI assistant")
public class ChatApiController {
    
    private final AiOrchestrationService orchestrationService;
    private final ConversationService conversationService;
    
    @PostMapping("/chat")
    @Operation(
        summary = "Send a chat message",
        description = "Send a message to the AI assistant and receive a response. " +
                     "The system automatically routes queries to local or cloud LLM based on content.",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successful response",
                content = @Content(schema = @Schema(implementation = ChatResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Server error")
        }
    )
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody @Parameter(description = "Chat request containing the user message") 
            ChatRequest request) {
        
        log.info("Received chat request: {}", request.getMessage());

        // Determine conversation ID first (needed for loading history)
        String conversationId = request.getConversationId() != null
            ? request.getConversationId()
            : conversationService.createConversation().getId();

        // Process query with conversation context
        AiOrchestrationService.QueryResult result = orchestrationService.processQuery(
            request.getMessage(), conversationId);

        // Save messages to database
        conversationService.addMessage(conversationId, "user", request.getMessage(), null);
        conversationService.addMessage(conversationId, "assistant", result.getResponse(), result.getTraceId());

        // Build response
        ChatResponse response = ChatResponse.builder()
                .response(result.getResponse())
                .conversationId(conversationId)
                .traceId(result.getTraceId())
                .success(result.isSuccess())
                .error(result.getError())
                .durationMs(result.getTotalDurationMs())
                .anonymizedEntities(result.getAnonymizedEntities())
                .toolsUsed(result.getToolsUsed())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Send a chat message with streaming response",
        description = "Send a message and receive the response as a stream of Server-Sent Events. " +
                     "Each event contains a chunk of the response text.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Streaming response"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "500", description = "Server error")
        }
    )
    public Flux<String> chatStream(
            @Valid @RequestBody @Parameter(description = "Chat request containing the user message")
            ChatRequest request) {

        log.info("Received streaming chat request: {}", request.getMessage());

        // Determine conversation ID first
        String conversationId = request.getConversationId() != null
            ? request.getConversationId()
            : conversationService.createConversation().getId();

        // Save user message immediately
        conversationService.addMessage(conversationId, "user", request.getMessage(), null);

        // Stream response and collect full response for saving
        StringBuilder fullResponse = new StringBuilder();

        return orchestrationService.processQueryStream(request.getMessage(), conversationId)
            .doOnNext(chunk -> fullResponse.append(chunk))
            .doOnComplete(() -> {
                // Save the complete response after streaming finishes
                conversationService.addMessage(conversationId, "assistant", fullResponse.toString(), null);
                log.info("Streaming complete, saved response ({} chars)", fullResponse.length());
            })
            .doOnError(e -> log.error("Streaming error: {}", e.getMessage()));
    }

    @GetMapping("/conversation/{id}")
    @Operation(
        summary = "Get conversation history",
        description = "Retrieve a conversation with all its messages",
        responses = {
            @ApiResponse(responseCode = "200", description = "Conversation found"),
            @ApiResponse(responseCode = "404", description = "Conversation not found")
        }
    )
    public ResponseEntity<ConversationEntity> getConversation(
            @PathVariable @Parameter(description = "Conversation ID") String id) {

        ConversationEntity conversation = conversationService.getConversationWithMessages(id);
        return ResponseEntity.ok(conversation);
    }
}
