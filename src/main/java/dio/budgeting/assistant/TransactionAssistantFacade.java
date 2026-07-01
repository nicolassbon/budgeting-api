package dio.budgeting.assistant;

import dio.budgeting.application.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Infrastructure-owned AI orchestrator for transcription, prompt interpretation, and audio responses.
 * The interpretation result is produced as a draft and must not persist data before user confirmation.
 */
@Component
public class TransactionAssistantFacade implements TransactionAssistant {

    private static final String INTERPRETATION_SYSTEM_PROMPT = "Eres un asistente financiero. Tu tarea es extraer la información de gastos de un texto del usuario y estructurarla. Extrae la descripción, el monto numérico (siempre en centavos como entero, por ejemplo 70 pesos = 7000, 12.30 pesos = 1230, 1 peso = 100) y la categoría más adecuada (COMIDA, SUPERMERCADO, FARMACIA, ROPA, TRANSPORTE, VIVIENDA, HOGAR, SERVICIOS, ENTRETENIMIENTO, EDUCACION, SALUD, CUIDADO_PERSONAL, MASCOTAS, SUSCRIPCIONES, REGALOS, IMPUESTOS, DEUDAS, OTROS). Si algún campo no se puede inferir con certeza absoluta del texto, ponlo como null. Devolverás la estructura sin persistir ninguna transacción.";
    private static final Logger log = LoggerFactory.getLogger(TransactionAssistantFacade.class);

    private final TranscriptionModel transcriptionModel;
    private final ChatClient transactionChatClient;
    private final ChatClient interpretationChatClient;
    private final TextToSpeechModel textToSpeechModel;

    public TransactionAssistantFacade(TransactionService transactionService,
                                      TranscriptionModel transcriptionModel,
                                      ChatClient.Builder chatClientBuilder,
                                      @Value("classpath:/prompts/system-message.st") Resource systemPrompt,
                                      TextToSpeechModel textToSpeechModel) throws IOException {
        this.transcriptionModel = transcriptionModel;
        this.interpretationChatClient = chatClientBuilder
                .defaultSystem(INTERPRETATION_SYSTEM_PROMPT)
                .build();
        this.transactionChatClient = chatClientBuilder
                .defaultSystem(systemPrompt.getContentAsString(StandardCharsets.UTF_8))
                .defaultTools(transactionService)
                .build();
        this.textToSpeechModel = textToSpeechModel;
    }

    @Override
    public ResponseEntity<Resource> transcribe(MultipartFile file) {
        AssistantInputValidator.validateAudioFile(file);
        log.info("transaction_ai_transcribe_started filename={} size={} contentType={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());

        String userMessage;
        try {
            userMessage = transcriptionModel.transcribe(file.getResource());
        } catch (RuntimeException exception) {
            throw new AssistantIntegrationException("Failed to transcribe the provided audio", exception);
        }

        String result;
        try {
            result = transactionChatClient.prompt().user(userMessage).call().content();
        } catch (RuntimeException exception) {
            throw new AssistantIntegrationException("Failed to generate a transaction response", exception);
        }

        byte[] audio;
        try {
            audio = textToSpeechModel.call(result);
        } catch (RuntimeException exception) {
            throw new AssistantIntegrationException("Failed to synthesize the transaction audio response", exception);
        }

        log.info("transaction_ai_transcribe_completed filename={} audioBytes={}",
                file.getOriginalFilename(), audio.length);
        return AssistantHttpResponses.mp3Attachment(audio);
    }

    @Override
    public TransactionDraft interpret(String prompt) {
        AssistantInputValidator.validatePrompt(prompt);
        log.info("transaction_ai_interpret_started promptLength={}", prompt.length());

        try {
            return interpretationChatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(TransactionDraft.class);
        } catch (RuntimeException exception) {
            throw new AssistantIntegrationException("Failed to interpret the transaction prompt", exception);
        }
    }
}
