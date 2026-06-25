package dio.budgeting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class ToolCallingIT {

    @Autowired
    OpenAiChatModel openAiChatModel;

    static class MathTools {
        @Tool(description = "suma dos numeros enteros, a y b")
        public int sum(int a, int b) {
            return a + b;
        }

        @Tool(description = "resta dos numeros enteros, a y b")
        public int diff(int a, int b) {
            return a - b;
        }
    }

    @Test
    void should_executeSum_when_prompted() {
        var chatClient = ChatClient.builder(openAiChatModel)
                .defaultSystem("Eres un matematico experto")
                .defaultTools(new MathTools())
                .build();

        var response = chatClient.prompt("Suma 10 mas 20, despues resta 30 al resultado anterior. Mostra solo el resultado final sin explicaciones")
                .call()
                .content();

        assertThat(response.toLowerCase()).containsAnyOf("0", "cero");
        System.out.println(response);
    }
}
