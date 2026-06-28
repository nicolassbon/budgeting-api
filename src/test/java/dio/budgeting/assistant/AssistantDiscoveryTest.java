package dio.budgeting.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.ai.openai.api-key=dummy-key"
})
class AssistantDiscoveryTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldDiscoverAndRegisterRootAssistantEndpoints() {
        // Verify that component scanning registers the controllers
        assertThat(applicationContext.containsBean("chatClientController")).isTrue();
        assertThat(applicationContext.containsBean("chatModelController")).isTrue();
        assertThat(applicationContext.containsBean("transcriptionController")).isTrue();
        assertThat(applicationContext.containsBean("textToSpeechController")).isTrue();
    }
}
