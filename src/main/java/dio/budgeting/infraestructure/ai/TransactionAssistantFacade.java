package dio.budgeting.infraestructure.ai;

import dio.budgeting.application.TransactionService;
import dio.budgeting.config.InterpretProperties;
import dio.budgeting.infraestructure.http.assistant.AssistantHttpResponses;
import jakarta.annotation.PreDestroy;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Infrastructure-owned AI orchestrator for transcription, prompt interpretation, and audio responses.
 * The interpretation result is produced as a draft and must not persist data before user confirmation.
 */
@Component
public class TransactionAssistantFacade implements TransactionAssistant {

    private static final Logger log = LoggerFactory.getLogger(TransactionAssistantFacade.class);
    private static final int INTERPRETATION_EXECUTOR_THREADS = 4;
    private static final int INTERPRETATION_EXECUTOR_QUEUE_CAPACITY = 16;

    private final TranscriptionModel transcriptionModel;
    private final ChatClient transactionChatClient;
    private final ChatClient interpretationChatClient;
    private final TextToSpeechModel textToSpeechModel;
    private final InterpretProperties interpretProperties;
    private final Clock clock;
    private final ExecutorService interpretationExecutor;

    public TransactionAssistantFacade(TransactionService transactionService,
                                      TranscriptionModel transcriptionModel,
                                      ChatClient.Builder chatClientBuilder,
                                      @Value("classpath:/prompts/system-message.st") Resource systemPrompt,
                                      TextToSpeechModel textToSpeechModel,
                                      InterpretProperties interpretProperties,
                                      Clock clock) throws IOException {
        this.transcriptionModel = transcriptionModel;
        this.interpretationChatClient = chatClientBuilder
                .defaultSystem(interpretProperties.systemPrompt().getContentAsString(StandardCharsets.UTF_8))
                .build();
        this.transactionChatClient = chatClientBuilder
                .defaultSystem(systemPrompt.getContentAsString(StandardCharsets.UTF_8))
                .defaultTools(transactionService)
                .build();
        this.textToSpeechModel = textToSpeechModel;
        this.interpretProperties = interpretProperties;
        this.clock = clock;
        this.interpretationExecutor = new ThreadPoolExecutor(
                INTERPRETATION_EXECUTOR_THREADS,
                INTERPRETATION_EXECUTOR_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(INTERPRETATION_EXECUTOR_QUEUE_CAPACITY),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @PreDestroy
    void shutdownInterpretationExecutor() {
        interpretationExecutor.shutdownNow();
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
    public InterpretationResult interpret(String prompt) {
        AssistantInputValidator.validatePrompt(prompt, interpretProperties.minPromptLength(), interpretProperties.maxPromptLength());
        Instant started = Instant.now(clock);

        try {
            InterpretationPayload payload = ArgentineAmountCentavosNormalizer.normalize(prompt, callInterpretationClient(prompt));
            InterpretationResult result = InterpretationResult.fromPayload(payload);
            logInterpretation(prompt, started, outcome(result));
            return result;
        } catch (AssistantTimeoutException exception) {
            logInterpretation(prompt, started, "timeout");
            throw exception;
        } catch (RuntimeException exception) {
            if (isTimeout(exception)) {
                AssistantTimeoutException timeout = new AssistantTimeoutException("Interpretation request timed out", exception);
                logInterpretation(prompt, started, "timeout");
                throw timeout;
            }
            logInterpretation(prompt, started, "integration_error");
            throw new AssistantIntegrationException("Failed to interpret the transaction prompt", exception);
        }
    }

    private InterpretationPayload callInterpretationClient(String prompt) {
        Duration timeout = interpretProperties.timeout();
        CompletableFuture<InterpretationPayload> future;
        try {
            future = CompletableFuture.supplyAsync(() -> interpretationChatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(InterpretationPayload.class), interpretationExecutor);
        } catch (RejectedExecutionException exception) {
            throw new AssistantIntegrationException("Interpretation executor is saturated", exception);
        }
        try {
            InterpretationPayload payload = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (payload == null) {
                return new InterpretationPayload(null, null, null, null);
            }
            return payload;
        } catch (TimeoutException exception) {
            // Spring AI's blocking chat client may not interrupt the underlying provider call immediately.
            // Cancellation still prevents this request from waiting past our HTTP contract, and the
            // dedicated fixed-size executor bounds the number of provider calls that can linger locally.
            future.cancel(true);
            throw new AssistantTimeoutException("Interpretation request timed out", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssistantTimeoutException("Interpretation request timed out", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssistantIntegrationException("Failed to interpret the transaction prompt", cause);
        }
    }

    private void logInterpretation(String prompt, Instant started, String outcome) {
        AssistantPromptTelemetry.logCompletion(log, prompt, started, Instant.now(clock), outcome, "openai-chat-client");
    }

    private String outcome(InterpretationResult result) {
        return switch (result.status()) {
            case OK -> "ok";
            case INCOMPLETE -> "incomplete";
            case OUT_OF_SCOPE -> "out_of_scope";
        };
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}
