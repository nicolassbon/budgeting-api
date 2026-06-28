package dio.budgeting.infraestructure.http;

import dio.budgeting.assistant.AssistantInputValidator;
import dio.budgeting.assistant.TransactionAssistant;
import dio.budgeting.assistant.TransactionDraft;
import dio.budgeting.application.TransactionService;
import dio.budgeting.domain.Category;
import dio.budgeting.infraestructure.http.request.TransactionRequest;
import dio.budgeting.infraestructure.http.response.TransactionResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionAssistant transactionAssistant;

    public TransactionController(TransactionService transactionService,
                                 TransactionAssistant transactionAssistant) {
        this.transactionService = transactionService;
        this.transactionAssistant = transactionAssistant;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@RequestBody TransactionRequest request) {
        var transaction = transactionService.create(request.toInput());
        return TransactionResponse.from(transaction);
    }

    @GetMapping("/{category}")
    public List<TransactionResponse> findAllTransactionsByCategory(@PathVariable Category category) {
        return transactionService.findAllByCategory(category)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @PostMapping(value = "/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "audio/mp3")
    public ResponseEntity<Resource> transcribe(@RequestParam("file") MultipartFile file) {
        AssistantInputValidator.validateAudioFile(file);
        return transactionAssistant.transcribe(file);
    }

    public record InterpretRequest(String prompt) {}

    @PostMapping("/interpret")
    public TransactionDraft interpret(@RequestBody InterpretRequest request) {
        AssistantInputValidator.validatePrompt(request.prompt());
        return transactionAssistant.interpret(request.prompt());
    }
}
