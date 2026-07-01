package dio.budgeting.infraestructure.ai;

import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

public final class AssistantPromptTelemetry {

    private AssistantPromptTelemetry() {
    }

    public static String truncatedSha256(String prompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(prompt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest unavailable", exception);
        }
    }

    public static void logCompletion(Logger log, String prompt, Instant started, Instant completed, String outcome) {
        String safePrompt = prompt == null ? "" : prompt;
        long latencyMs = Duration.between(started, completed).toMillis();
        log.info("transaction_ai_interpret_completed promptLength={} promptHash={} latencyMs={} outcome={}",
                safePrompt.length(), truncatedSha256(safePrompt), latencyMs, outcome);
    }

    public static void logCompletion(Logger log,
                                     String prompt,
                                     Instant started,
                                     Instant completed,
                                     String outcome,
                                     String model) {
        String safePrompt = prompt == null ? "" : prompt;
        long latencyMs = Duration.between(started, completed).toMillis();
        log.info("transaction_ai_interpret_completed promptLength={} promptHash={} latencyMs={} outcome={} model={}",
                safePrompt.length(), truncatedSha256(safePrompt), latencyMs, outcome, model);
    }
}
