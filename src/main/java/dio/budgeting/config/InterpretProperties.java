package dio.budgeting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("ai.interpret")
public record InterpretProperties(RateLimit rateLimit, Duration timeout, int minPromptLength) {
    public InterpretProperties {
        rateLimit = rateLimit == null ? new RateLimit(20) : rateLimit;
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        minPromptLength = minPromptLength <= 0 ? 3 : minPromptLength;
    }

    public record RateLimit(int requestsPerMinute) {
        public RateLimit {
            requestsPerMinute = requestsPerMinute <= 0 ? 20 : requestsPerMinute;
        }
    }
}
