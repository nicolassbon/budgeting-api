package dio.budgeting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.time.Duration;

@ConfigurationProperties("ai.interpret")
public record InterpretProperties(RateLimit rateLimit,
                                  Duration timeout,
                                  int minPromptLength,
                                  int maxPromptLength,
                                  Resource systemPrompt) {
    public static final int DEFAULT_MIN_PROMPT_LENGTH = 3;
    public static final int DEFAULT_MAX_PROMPT_LENGTH = 4_000;
    public static final String DEFAULT_SYSTEM_PROMPT_LOCATION = "prompts/interpretation-system-message.st";

    public InterpretProperties {
        rateLimit = rateLimit == null ? new RateLimit(20) : rateLimit;
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        minPromptLength = minPromptLength <= 0 ? DEFAULT_MIN_PROMPT_LENGTH : minPromptLength;
        maxPromptLength = maxPromptLength <= 0 ? DEFAULT_MAX_PROMPT_LENGTH : maxPromptLength;
        systemPrompt = systemPrompt == null ? new ClassPathResource(DEFAULT_SYSTEM_PROMPT_LOCATION) : systemPrompt;
    }

    public record RateLimit(int requestsPerMinute) {
        public RateLimit {
            requestsPerMinute = requestsPerMinute <= 0 ? 20 : requestsPerMinute;
        }
    }
}
