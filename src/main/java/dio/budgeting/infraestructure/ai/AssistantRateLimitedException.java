package dio.budgeting.infraestructure.ai;

public class AssistantRateLimitedException extends RuntimeException {
    private final long retryAfterSeconds;

    public AssistantRateLimitedException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
