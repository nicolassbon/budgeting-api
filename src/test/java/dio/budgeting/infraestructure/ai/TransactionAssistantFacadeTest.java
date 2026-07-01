package dio.budgeting.infraestructure.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dio.budgeting.application.TransactionService;
import dio.budgeting.config.InterpretProperties;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionAssistantFacadeTest {

    private ListAppender<ILoggingEvent> logAppender;

    private TransactionService transactionService;
    private TranscriptionModel transcriptionModel;
    private TextToSpeechModel textToSpeechModel;
    private ChatClient.Builder chatClientBuilder;
    private ChatClient aiChatClient;
    private ChatClient interpretationChatClient;

    @BeforeEach
    void setUp() {
        transactionService = mock(TransactionService.class);
        transcriptionModel = mock(TranscriptionModel.class);
        textToSpeechModel = mock(TextToSpeechModel.class);
        chatClientBuilder = mock(ChatClient.Builder.class, Answers.RETURNS_SELF);
        aiChatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        interpretationChatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);

        when(chatClientBuilder.build()).thenReturn(interpretationChatClient, aiChatClient);

        Logger logger = (Logger) LoggerFactory.getLogger(TransactionAssistantFacade.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(TransactionAssistantFacade.class);
        logger.detachAppender(logAppender);
    }

    @Test
    void shouldOrchestrateTranscriptionToolChatAndTtsInSequence() throws Exception {
        when(transcriptionModel.transcribe(any())).thenReturn("buy coffee and bread");
        when(aiChatClient.prompt().user("buy coffee and bread").call().content()).thenReturn("structured transaction");
        when(textToSpeechModel.call("structured transaction")).thenReturn("audio-bytes".getBytes(StandardCharsets.UTF_8));

        TransactionAssistantFacade facade = newFacade();
        ResponseEntity<?> response = facade.transcribe(new MockMultipartFile(
                "file",
                "audio.wav",
                "audio/wav",
                "audio".getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo(ContentDisposition.attachment().filename("audio.mp3").build().toString());
        assertThat(response.getBody()).isInstanceOf(ByteArrayResource.class);
        assertThat(((ByteArrayResource) response.getBody()).getByteArray()).isEqualTo("audio-bytes".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldLoadSystemPromptFromClasspathForTransactionChatClient() throws Exception {
        Resource systemPrompt = mock(Resource.class);
        when(systemPrompt.getContentAsString(StandardCharsets.UTF_8)).thenReturn("system prompt from file");

        var constructor = TransactionAssistantFacade.class.getConstructor(
                TransactionService.class,
                TranscriptionModel.class,
                ChatClient.Builder.class,
                Resource.class,
                TextToSpeechModel.class,
                InterpretProperties.class,
                Clock.class
        );

        var value = constructor.getParameters()[3].getAnnotation(Value.class);
        assertThat(value).isNotNull();
        assertThat(value.value()).isEqualTo("classpath:/prompts/system-message.st");

        newFacade(systemPrompt);

        verify(systemPrompt).getContentAsString(StandardCharsets.UTF_8);
        verify(chatClientBuilder).defaultSystem("system prompt from file");
    }

    @Test
    void shouldRegisterTransactionServiceAsDefaultTools() throws Exception {
        Resource systemPrompt = mock(Resource.class);
        when(systemPrompt.getContentAsString(StandardCharsets.UTF_8)).thenReturn("system prompt from file");

        newFacade(systemPrompt);

        verify(chatClientBuilder).defaultTools(transactionService);
    }

    @Test
    void shouldOrchestrateInterpretationWithASeparatePromptClient() throws Exception {
        InterpretationPayload payload = new InterpretationPayload(null, "Coffee and bread", 2300L, Category.COMIDA);
        String expectedInterpretationPrompt = expectedInterpretationPrompt();

        when(interpretationChatClient.prompt().user("latte and bread").call().entity(InterpretationPayload.class)).thenReturn(payload);

        TransactionAssistantFacade facade = newFacade();
        InterpretationResult result = facade.interpret("latte and bread");

        assertThat(result.status()).isEqualTo(InterpretationStatus.OK);
        assertThat(result.description()).isEqualTo("Coffee and bread");
        assertThat(result.amount()).isEqualTo(2300L);
        assertThat(result.category()).isEqualTo(Category.COMIDA);
        assertThat(expectedInterpretationPrompt)
                .contains("gastos personales", "OUT_OF_SCOPE", "No persistas nada")
                .doesNotContain("Interpret only personal expense descriptions", "Do not persist anything");
        verify(chatClientBuilder).defaultSystem(expectedInterpretationPrompt);
    }

    @Test
    void shouldLoadInterpretationPromptFromConfiguredResource() throws Exception {
        Resource interpretationPrompt = mock(Resource.class);
        when(interpretationPrompt.getContentAsString(StandardCharsets.UTF_8)).thenReturn("interpretation prompt from file");

        newFacade(new ByteArrayResource("system prompt".getBytes(StandardCharsets.UTF_8)), interpretationPrompt);

        verify(interpretationPrompt).getContentAsString(StandardCharsets.UTF_8);
        verify(chatClientBuilder).defaultSystem("interpretation prompt from file");
    }


    @Test
    void shouldReturnIncompleteStatusForPartialInterpretation() throws Exception {
        when(interpretationChatClient.prompt().user("coffee maybe").call().entity(InterpretationPayload.class))
                .thenReturn(new InterpretationPayload(null, "Coffee", null, null));

        InterpretationResult result = newFacade().interpret("coffee maybe");

        assertThat(result.status()).isEqualTo(InterpretationStatus.INCOMPLETE);
        assertThat(result.description()).isEqualTo("Coffee");
        assertThat(result.amount()).isNull();
        assertThat(result.category()).isNull();
    }

    @Test
    void shouldNormalizeSingleArgentineMilAmountReturnedInPesosToCentavos() throws Exception {
        when(interpretationChatClient.prompt().user("Gasté 100 mil pesos en ropa").call().entity(InterpretationPayload.class))
                .thenReturn(new InterpretationPayload(null, "Ropa", 100_000L, Category.ROPA));

        InterpretationResult result = newFacade().interpret("Gasté 100 mil pesos en ropa");

        assertThat(result.status()).isEqualTo(InterpretationStatus.OK);
        assertThat(result.amount()).isEqualTo(10_000_000L);
        assertThat(result.category()).isEqualTo(Category.ROPA);
    }

    @Test
    void shouldKeepAlreadyCorrectCentavosForSingleArgentineMilAmount() throws Exception {
        when(interpretationChatClient.prompt().user("Gasté 100 mil pesos en ropa").call().entity(InterpretationPayload.class))
                .thenReturn(new InterpretationPayload(null, "Ropa", 10_000_000L, Category.ROPA));

        InterpretationResult result = newFacade().interpret("Gasté 100 mil pesos en ropa");

        assertThat(result.status()).isEqualTo(InterpretationStatus.OK);
        assertThat(result.amount()).isEqualTo(10_000_000L);
        assertThat(result.category()).isEqualTo(Category.ROPA);
    }

    @Test
    void shouldWrapInterpretationTimeoutsAsAssistantTimeout() throws Exception {
        when(interpretationChatClient.prompt().user("latte and bread").call().entity(InterpretationPayload.class))
                .thenThrow(new RuntimeException(new TimeoutException("provider timeout with secret prompt")));

        assertThatThrownBy(() -> newFacade().interpret("latte and bread"))
                .isInstanceOf(AssistantTimeoutException.class)
                .hasMessage("Interpretation request timed out");
    }

    @Test
    void shouldWrapInterpretationFailuresAsAssistantIntegrationErrors() throws Exception {
        when(interpretationChatClient.prompt().user("latte and bread").call().entity(InterpretationPayload.class))
                .thenThrow(new IllegalStateException("provider leaked detail"));

        assertThatThrownBy(() -> newFacade().interpret("latte and bread"))
                .isInstanceOf(AssistantIntegrationException.class)
                .hasMessage("Failed to interpret the transaction prompt");
    }

    @Test
    void shouldReturnOutOfScopeWhenModelSignalsStatus() throws Exception {
        when(interpretationChatClient.prompt().user("write a poem").call().entity(InterpretationPayload.class))
                .thenReturn(new InterpretationPayload("OUT_OF_SCOPE", null, null, null));

        InterpretationResult result = newFacade().interpret("write a poem");

        assertThat(result.status()).isEqualTo(InterpretationStatus.OUT_OF_SCOPE);
        assertThat(result.description()).isNull();
        assertThat(result.amount()).isNull();
        assertThat(result.category()).isNull();
    }

    @Test
    void shouldLogOneCompletionEventForOutOfScopeInterpretation() throws Exception {
        when(interpretationChatClient.prompt().user("write a poem").call().entity(InterpretationPayload.class))
                .thenReturn(new InterpretationPayload("OUT_OF_SCOPE", null, null, null));

        newFacade().interpret("write a poem");

        List<String> completionEvents = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("transaction_ai_interpret_completed"))
                .toList();

        assertThat(completionEvents).singleElement().satisfies(message -> assertThat(message)
                .contains("outcome=out_of_scope"));
    }

    @Test
    void shouldRedactPromptTextInInterpretationLogs() throws Exception {
        String prompt = "my secret prompt text";
        when(interpretationChatClient.prompt().user(prompt).call().entity(InterpretationPayload.class))
                .thenReturn(new InterpretationPayload(null, "Coffee", 2300L, Category.COMIDA));

        newFacade().interpret(prompt);

        List<String> messages = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertThat(messages).anySatisfy(message -> {
            assertThat(message).contains("transaction_ai_interpret_completed");
            assertThat(message).contains("promptLength=");
            assertThat(message).contains("promptHash=");
            assertThat(message).contains("latencyMs=");
            assertThat(message).contains("outcome=ok");
            assertThat(message).doesNotContain(prompt);
        });
    }

    @Test
    void shouldRejectInvalidAudioInputBeforeCallingAiModels() throws Exception {
        TransactionAssistantFacade facade = newFacade();

        assertThatThrownBy(() -> facade.transcribe(new MockMultipartFile(
                        "file",
                        "notes.txt",
                        "text/plain",
                        "audio".getBytes(StandardCharsets.UTF_8)
                )))
                .isInstanceOf(AssistantValidationException.class)
                .hasMessage("Audio file content type must be audio/* or application/octet-stream");
    }

    @Test
    void shouldWrapTranscriptionFailuresAsAssistantIntegrationErrors() throws Exception {
        when(transcriptionModel.transcribe(any())).thenThrow(new IllegalStateException("openai down"));

        TransactionAssistantFacade facade = newFacade();

        assertThatThrownBy(() -> facade.transcribe(new MockMultipartFile(
                        "file",
                        "audio.wav",
                        "audio/wav",
                        "audio".getBytes(StandardCharsets.UTF_8)
                )))
                .isInstanceOf(AssistantIntegrationException.class)
                .hasMessage("Failed to transcribe the provided audio")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectBlankInterpretPrompt() throws Exception {
        TransactionAssistantFacade facade = newFacade();

        assertThatThrownBy(() -> facade.interpret("   "))
                .isInstanceOf(AssistantValidationException.class)
                .hasMessage("Prompt must not be blank");
    }

    private TransactionAssistantFacade newFacade() throws Exception {
        return newFacade(new ByteArrayResource("system prompt".getBytes(StandardCharsets.UTF_8)), interpretationPromptResource());
    }

    private TransactionAssistantFacade newFacade(Resource systemPrompt) throws Exception {
        return newFacade(systemPrompt, interpretationPromptResource());
    }

    private TransactionAssistantFacade newFacade(Resource systemPrompt, Resource interpretationPrompt) throws Exception {
        return new TransactionAssistantFacade(
                transactionService,
                transcriptionModel,
                chatClientBuilder,
                systemPrompt,
                textToSpeechModel,
                new InterpretProperties(
                        new InterpretProperties.RateLimit(20),
                        Duration.ofSeconds(5),
                        3,
                        4_000,
                        interpretationPrompt
                ),
                Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private Resource interpretationPromptResource() {
        return new ClassPathResource("prompts/interpretation-system-message.st");
    }

    private String expectedInterpretationPrompt() throws Exception {
        return interpretationPromptResource().getContentAsString(StandardCharsets.UTF_8);
    }
}
