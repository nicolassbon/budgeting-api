package dio.budgeting.application.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("auth.password-reset")
public record PasswordResetProperties(
        String resetBaseUrl,
        Duration tokenTtl
) {
    public PasswordResetProperties {
        if (resetBaseUrl == null || resetBaseUrl.isBlank()) {
            resetBaseUrl = "http://localhost:5173/reset-password";
        }
        if (tokenTtl == null) {
            tokenTtl = Duration.ofMinutes(30);
        }
    }
}
