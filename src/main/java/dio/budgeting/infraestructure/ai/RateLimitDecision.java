package dio.budgeting.infraestructure.ai;

public record RateLimitDecision(boolean allowed, int remaining, long resetEpochSeconds, long retryAfterSeconds) {
    public static RateLimitDecision allowed(int remaining, long resetEpochSeconds) {
        return new RateLimitDecision(true, remaining, resetEpochSeconds, 0);
    }

    public static RateLimitDecision denied(long retryAfterSeconds) {
        return new RateLimitDecision(false, 0, 0, Math.max(1, retryAfterSeconds));
    }
}
