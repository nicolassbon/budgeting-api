package dio.budgeting.infraestructure.ai;

public interface AiInterpretRateLimiter {
    RateLimitDecision check(String identityKey);
}
