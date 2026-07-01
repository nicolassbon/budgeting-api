package dio.budgeting.infraestructure.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class OpenAiChatModelIT {

    @Autowired
    private OpenAiApi  openAiApi;

    @Test
    void should_receiveResponse_when_chatModelIsCalled() {
        var options = OpenAiChatOptions.builder()
                .model("gpt-4o-mini")
                .temperature(0.8)
                .responseFormat(ResponseFormat.builder().type(ResponseFormat.Type.TEXT).build())
                .build();

        var chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        var response = chatModel.call("Eres un registro de bugdeting con descripción de gasto, valor en pesos argentinos y local");

        assertThat(response).isNotBlank();
        System.out.println(response);
    }
}
