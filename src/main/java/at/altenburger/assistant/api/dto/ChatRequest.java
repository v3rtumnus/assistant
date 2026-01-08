package at.altenburger.assistant.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {
    
    @NotBlank(message = "Message cannot be blank")
    @Size(max = 5000, message = "Message must not exceed 5000 characters")
    private String message;
    
    private String conversationId;
}
