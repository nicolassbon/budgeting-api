package dio.budgeting.infraestructure.http.assistant;

import dio.budgeting.infraestructure.ai.AssistantIntegrationException;
import dio.budgeting.infraestructure.ai.AssistantValidationException;
import dio.budgeting.infraestructure.http.TransactionController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @ExceptionHandler(AssistantValidationException.class)
    public ResponseEntity<AssistantErrorResponse> handleValidation(AssistantValidationException exception) {
        log.warn("assistant_request_validation_failed message={}", exception.getMessage());
        return ResponseEntity.badRequest().body(new AssistantErrorResponse("assistant_validation_error", exception.getMessage()));
    }

    @ExceptionHandler(AssistantIntegrationException.class)
    public ResponseEntity<AssistantErrorResponse> handleIntegration(AssistantIntegrationException exception) {
        log.error("assistant_integration_failed message={}", exception.getMessage(), exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new AssistantErrorResponse("assistant_integration_error", exception.getMessage()));
    }
}
