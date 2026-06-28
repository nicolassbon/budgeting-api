package dio.budgeting.assistant;

import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TextToSpeechController {

    private final TextToSpeechModel textToSpeechModel;

    public TextToSpeechController(TextToSpeechModel textToSpeechModel) {
        this.textToSpeechModel = textToSpeechModel;
    }

    @PostMapping(value = "/sinthesize", produces = "audio/mp3")
    public ResponseEntity<Resource> sinthesize(@RequestBody SynthesizeRequest request) {
        AssistantInputValidator.validatePrompt(request.text);

        byte[] audio;
        try {
            audio = textToSpeechModel.call(request.text);
        } catch (RuntimeException exception) {
            throw new AssistantIntegrationException("Failed to synthesize the requested text", exception);
        }

        return AssistantHttpResponses.mp3Attachment(audio);
    }

    record SynthesizeRequest(String text) {

    }
}
