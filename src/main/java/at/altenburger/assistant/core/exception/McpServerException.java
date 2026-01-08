package at.altenburger.assistant.core.exception;

public class McpServerException extends AssistantException {
    public McpServerException(String message) {
        super(message);
    }

    public McpServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
