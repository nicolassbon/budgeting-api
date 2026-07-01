package dio.budgeting.infraestructure.http.assistant;

import dio.budgeting.infraestructure.ai.AssistantInputValidator;
import dio.budgeting.infraestructure.ai.AssistantIntegrationException;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class TranscriptionController {
    private final TranscriptionModel transcriptionModel;

    public TranscriptionController(TranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String transcribe(@RequestParam("file") MultipartFile file) {
        AssistantInputValidator.validateAudioFile(file);

        try {
            return transcriptionModel.transcribe(file.getResource());
        } catch (RuntimeException exception) {
            throw new AssistantIntegrationException("Failed to transcribe the provided audio", exception);
        }
    }

}
