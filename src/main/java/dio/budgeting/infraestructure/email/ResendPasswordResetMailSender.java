package dio.budgeting.infraestructure.email;

import dio.budgeting.application.auth.PasswordResetEmail;
import dio.budgeting.application.auth.PasswordResetMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class ResendPasswordResetMailSender implements PasswordResetMailSender {
    private static final Logger log = LoggerFactory.getLogger(ResendPasswordResetMailSender.class);

    private final RestClient restClient;
    private final ResendProperties properties;

    public ResendPasswordResetMailSender(RestClient.Builder restClientBuilder, ResendProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public void send(PasswordResetEmail email) {
        if (!StringUtils.hasText(properties.apiKey())) {
            log.warn("password_reset_email_skipped reason=missing_resend_api_key to={}", email.to());
            return;
        }

        try {
            restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(new ResendEmailRequest(
                            properties.sender(),
                            email.to(),
                            "Restablecer tu contraseña",
                            "Usá este enlace para restablecer tu contraseña: " + email.resetLink()
                    ))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            log.warn("password_reset_email_failed to={}", email.to(), exception);
        }
    }

    private record ResendEmailRequest(String from, String to, String subject, String text) {
    }
}
