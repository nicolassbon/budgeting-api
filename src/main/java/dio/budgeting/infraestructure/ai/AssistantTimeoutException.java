package dio.budgeting.infraestructure.ai;

public class AssistantTimeoutException extends RuntimeException {
    public AssistantTimeoutException(String message) {
        super(message);
    }

    public AssistantTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
