package dio.budgeting.assistant;

import dio.budgeting.application.TransactionService;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionAssistantFacadeTest {

    private static final String EXPECTED_INTERPRETATION_PROMPT = "Eres un asistente financiero. Tu tarea es extraer la información de gastos de un texto del usuario y estructurarla. Extrae la descripción, el monto numérico (siempre en centavos como entero, por ejemplo 70 pesos = 7000, 12.30 pesos = 1230, 1 peso = 100) y la categoría más adecuada (COMIDA, SUPERMERCADO, FARMACIA, ROPA, TRANSPORTE, VIVIENDA, HOGAR, SERVICIOS, ENTRETENIMIENTO, EDUCACION, SALUD, CUIDADO_PERSONAL, MASCOTAS, SUSCRIPCIONES, REGALOS, IMPUESTOS, DEUDAS, OTROS). Si algún campo no se puede inferir con certeza absoluta del texto, ponlo como null. Devolverás la estructura sin persistir ninguna transacción.";

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
                TextToSpeechModel.class
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
        TransactionDraft draft = new TransactionDraft("Coffee and bread", 2300L, Category.COMIDA);

        when(interpretationChatClient.prompt().user("latte and bread").call().entity(TransactionDraft.class)).thenReturn(draft);

        TransactionAssistantFacade facade = newFacade();
        TransactionDraft result = facade.interpret("latte and bread");

        assertThat(result.description()).isEqualTo("Coffee and bread");
        assertThat(result.amount()).isEqualTo(2300L);
        assertThat(result.category()).isEqualTo(Category.COMIDA);
        verify(chatClientBuilder).defaultSystem(EXPECTED_INTERPRETATION_PROMPT);
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
        return newFacade(new ByteArrayResource("system prompt".getBytes(StandardCharsets.UTF_8)));
    }

    private TransactionAssistantFacade newFacade(Resource systemPrompt) throws Exception {
        return new TransactionAssistantFacade(
                transactionService,
                transcriptionModel,
                chatClientBuilder,
                systemPrompt,
                textToSpeechModel
        );
    }
}
