package dio.budgeting.infraestructure.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("resend")
public record ResendProperties(
        String apiKey,
        String sender,
        String baseUrl
) {
    public ResendProperties {
        if (sender == null || sender.isBlank()) {
            sender = "no-reply@mail.nidoapp.online";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.resend.com";
        }
    }
}
