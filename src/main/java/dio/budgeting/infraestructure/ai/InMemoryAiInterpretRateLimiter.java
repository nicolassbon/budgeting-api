package dio.budgeting.infraestructure.ai;

import dio.budgeting.config.InterpretProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryAiInterpretRateLimiter implements AiInterpretRateLimiter {

    private static final long WINDOW_SECONDS = 60;
    private static final int MAX_BUCKETS = 1_000;

    private final InterpretProperties properties;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public InMemoryAiInterpretRateLimiter(InterpretProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public RateLimitDecision check(String identityKey) {
        long now = Instant.now(clock).getEpochSecond();
        cleanupExpiredBuckets(now);
        Bucket bucket = buckets.computeIfAbsent(identityKey, ignored -> {
            evictOldestBucketIfFull();
            return new Bucket(now);
        });
        synchronized (bucket) {
            if (now - bucket.windowStartEpochSeconds >= WINDOW_SECONDS) {
                bucket.windowStartEpochSeconds = now;
                bucket.count = 0;
            }

            int limit = properties.rateLimit().requestsPerMinute();
            long reset = bucket.windowStartEpochSeconds + WINDOW_SECONDS;
            if (bucket.count >= limit) {
                return RateLimitDecision.denied(reset - now);
            }

            bucket.count++;
            return RateLimitDecision.allowed(limit - bucket.count, reset);
        }
    }

    int bucketCount() {
        return buckets.size();
    }

    private void cleanupExpiredBuckets(long now) {
        buckets.entrySet().removeIf(entry -> now - entry.getValue().windowStartEpochSeconds >= WINDOW_SECONDS);
    }

    private void evictOldestBucketIfFull() {
        if (buckets.size() < MAX_BUCKETS) {
            return;
        }

        buckets.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .ifPresent(buckets::remove);
    }

    private static final class Bucket implements Comparable<Bucket> {
        private long windowStartEpochSeconds;
        private int count;

        private Bucket(long windowStartEpochSeconds) {
            this.windowStartEpochSeconds = windowStartEpochSeconds;
        }

        @Override
        public int compareTo(Bucket other) {
            return Long.compare(windowStartEpochSeconds, other.windowStartEpochSeconds);
        }
    }
}
