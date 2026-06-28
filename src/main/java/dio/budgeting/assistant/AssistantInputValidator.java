package dio.budgeting.assistant;

import org.springframework.web.multipart.MultipartFile;

public final class AssistantInputValidator {

    private static final long MAX_AUDIO_BYTES = 10 * 1024 * 1024;
    private static final int MAX_PROMPT_LENGTH = 4_000;

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
        if (prompt == null || prompt.isBlank()) {
            throw new AssistantValidationException("Prompt must not be blank");
        }

        if (prompt.length() > MAX_PROMPT_LENGTH) {
            throw new AssistantValidationException("Prompt exceeds the 4000 character limit");
        }
    }
}
