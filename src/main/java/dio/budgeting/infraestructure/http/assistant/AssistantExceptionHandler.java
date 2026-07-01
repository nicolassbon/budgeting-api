package dio.budgeting.infraestructure.http.assistant;

import dio.budgeting.infraestructure.ai.AssistantIntegrationException;
import dio.budgeting.infraestructure.ai.AssistantRateLimitedException;
import dio.budgeting.infraestructure.ai.AssistantSemanticRejectionException;
import dio.budgeting.infraestructure.ai.AssistantTimeoutException;
import dio.budgeting.infraestructure.ai.AssistantValidationException;
import dio.budgeting.infraestructure.http.TransactionController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        TransactionController.class,
        ChatClientController.class,
        ChatModelController.class,
        TranscriptionController.class,
        TextToSpeechController.class
})
public class AssistantExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AssistantExceptionHandler.class);
    private static final String INTEGRATION_FAILURE_MESSAGE = "Assistant integration failed";

    @ExceptionHandler(AssistantValidationException.class)
    public ResponseEntity<AssistantErrorResponse> handleValidation(AssistantValidationException exception) {
        log.warn("assistant_request_validation_failed message={}", exception.getMessage());
        return ResponseEntity.badRequest().body(error("assistant_validation_error", exception.getMessage()));
    }

    @ExceptionHandler(AssistantSemanticRejectionException.class)
    public ResponseEntity<AssistantErrorResponse> handleSemanticRejection(AssistantSemanticRejectionException exception) {
        log.info("assistant_semantic_rejection message={}", exception.getMessage());
        return ResponseEntity.unprocessableEntity().body(error("assistant_out_of_scope", exception.getMessage()));
    }

    @ExceptionHandler(AssistantRateLimitedException.class)
    public ResponseEntity<AssistantErrorResponse> handleRateLimited(AssistantRateLimitedException exception) {
        log.warn("assistant_rate_limited retryAfterSeconds={}", exception.retryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.retryAfterSeconds()))
                .body(error("assistant_rate_limited", exception.getMessage()));
    }

    @ExceptionHandler(AssistantTimeoutException.class)
    public ResponseEntity<AssistantErrorResponse> handleTimeout(AssistantTimeoutException exception) {
        log.error("assistant_timeout exceptionType={} causeType={}",
                exception.getClass().getSimpleName(), rootCauseType(exception));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(error("assistant_timeout", "Interpretation request timed out"));
    }

    @ExceptionHandler(AssistantIntegrationException.class)
    public ResponseEntity<AssistantErrorResponse> handleIntegration(AssistantIntegrationException exception) {
        log.error("assistant_integration_failed exceptionType={} causeType={}",
                exception.getClass().getSimpleName(), rootCauseType(exception));
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(error("assistant_integration_error", INTEGRATION_FAILURE_MESSAGE));
    }

    private AssistantErrorResponse error(String code, String message) {
        return new AssistantErrorResponse(code, message);
    }

    private String rootCauseType(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }
}
