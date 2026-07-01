package dio.budgeting.infraestructure.http.assistant;

import dio.budgeting.infraestructure.ai.AssistantInputValidator;
import dio.budgeting.infraestructure.ai.AssistantIntegrationException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChatClientController {

    private final ChatClient chatClient;

    public ChatClientController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @GetMapping("/chat-client")
    String chat(String prompt) {
        AssistantInputValidator.validatePrompt(prompt);

        try {
            return this.chatClient.prompt().user(prompt).call().content();
        } catch (RuntimeException exception) {
            throw new AssistantIntegrationException("Failed to complete the chat-client request", exception);
        }
    }
}
