package dio.budgeting.infraestructure.ai;

import dio.budgeting.config.InterpretProperties;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

public final class AssistantInputValidator {

    private static final long MAX_AUDIO_BYTES = 10 * 1024 * 1024;
    private static final List<String> INJECTION_MARKERS = List.of(
            "ignore previous instructions",
            "system prompt",
            "override instructions",
            "disregard previous instructions"
    );

    private AssistantInputValidator() {
    }

    public static void validateAudioFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AssistantValidationException("Audio file must not be empty");
        }

        if (file.getSize() > MAX_AUDIO_BYTES) {
            throw new AssistantValidationException("Audio file exceeds the 10 MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new AssistantValidationException("Audio file content type is required");
        }

        if (!contentType.startsWith("audio/") && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            throw new AssistantValidationException("Audio file content type must be audio/* or application/octet-stream");
        }
    }

    public static void validatePrompt(String prompt) {
        validatePrompt(prompt, InterpretProperties.DEFAULT_MIN_PROMPT_LENGTH, InterpretProperties.DEFAULT_MAX_PROMPT_LENGTH);
    }

    public static void validatePrompt(String prompt, int minPromptLength) {
        validatePrompt(prompt, minPromptLength, InterpretProperties.DEFAULT_MAX_PROMPT_LENGTH);
    }

    public static void validatePrompt(String prompt, int minPromptLength, int maxPromptLength) {
        if (prompt == null || prompt.isBlank()) {
            throw new AssistantValidationException("Prompt must not be blank");
        }

        if (prompt.length() > maxPromptLength) {
            throw new AssistantValidationException("Prompt exceeds the %d character limit".formatted(maxPromptLength));
        }

        String trimmed = prompt.trim();
        if (nonWhitespaceLength(prompt) < minPromptLength) {
            throw new AssistantValidationException("Prompt must contain at least %d non-whitespace characters".formatted(minPromptLength));
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        boolean containsMarker = INJECTION_MARKERS.stream().anyMatch(normalized::contains);
        if (containsMarker) {
            throw new AssistantValidationException("Prompt contains unsupported instruction-override markers");
        }
    }

    private static long nonWhitespaceLength(String prompt) {
        return prompt.chars()
                .filter(character -> !Character.isWhitespace(character))
                .count();
    }
}
