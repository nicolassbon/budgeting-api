package dio.budgeting.infraestructure.ai;

import dio.budgeting.config.InterpretProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AiInterpretRateLimiterTest {

    @Test
    void shouldAllowUntilConfiguredLimitThenDeny() {
        var limiter = new InMemoryAiInterpretRateLimiter(properties(2), fixedClock());

        RateLimitDecision first = limiter.check("session-1");
        RateLimitDecision second = limiter.check("session-1");
        RateLimitDecision third = limiter.check("session-1");

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(1);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isEqualTo(0);
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void shouldIsolateBucketsByIdentityKey() {
        var limiter = new InMemoryAiInterpretRateLimiter(properties(1), fixedClock());

        assertThat(limiter.check("user:1").allowed()).isTrue();
        assertThat(limiter.check("user:1").allowed()).isFalse();
        assertThat(limiter.check("user:2").allowed()).isTrue();
    }

    @Test
    void shouldResetBucketAfterWindowRollover() {
        var clock = new MutableClock(Instant.parse("2026-07-01T00:00:00Z"));
        var limiter = new InMemoryAiInterpretRateLimiter(properties(1), clock);

        assertThat(limiter.check("session-1").allowed()).isTrue();
        assertThat(limiter.check("session-1").allowed()).isFalse();

        clock.advance(Duration.ofSeconds(60));

        RateLimitDecision decision = limiter.check("session-1");
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(0);
    }

    @Test
    void shouldCleanupExpiredBucketsBeforeCreatingNewBuckets() {
        var clock = new MutableClock(Instant.parse("2026-07-01T00:00:00Z"));
        var limiter = new InMemoryAiInterpretRateLimiter(properties(2), clock);
        limiter.check("session-1");
        limiter.check("session-2");

        clock.advance(Duration.ofSeconds(60));
        limiter.check("session-3");

        assertThat(limiter.bucketCount()).isEqualTo(1);
    }

    @Test
    void shouldBoundBucketGrowthForManyDistinctSessions() {
        var limiter = new InMemoryAiInterpretRateLimiter(properties(2), fixedClock());

        for (int session = 0; session < 1_100; session++) {
            limiter.check("session-" + session);
        }

        assertThat(limiter.bucketCount()).isLessThanOrEqualTo(1_000);
    }

    private InterpretProperties properties(int requestsPerMinute) {
        return new InterpretProperties(new InterpretProperties.RateLimit(requestsPerMinute), Duration.ofSeconds(5), 3);
    }

    private Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
