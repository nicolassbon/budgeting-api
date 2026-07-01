package dio.budgeting.infraestructure.http.assistant;

import dio.budgeting.infraestructure.ai.AssistantInputValidator;
import dio.budgeting.infraestructure.ai.AssistantIntegrationException;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatModelController {

    private final OpenAiChatModel openAiChatModel;

    public ChatModelController(OpenAiChatModel openAiChatModel) {
        this.openAiChatModel = openAiChatModel;
    }

    @GetMapping("/chat-model")
    String chat(String prompt) {
        AssistantInputValidator.validatePrompt(prompt);

        try {
            return this.openAiChatModel.call(prompt);
        } catch (RuntimeException exception) {
            throw new AssistantIntegrationException("Failed to complete the chat-model request", exception);
        }
    }
}
