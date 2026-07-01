package dio.budgeting.infraestructure.http;

import dio.budgeting.application.TransactionService;
import dio.budgeting.application.input.TransactionHistoryFilters;
import dio.budgeting.application.security.AuthenticatedUserProvider;
import dio.budgeting.config.InterpretProperties;
import dio.budgeting.domain.Category;
import dio.budgeting.infraestructure.ai.AiInterpretRateLimiter;
import dio.budgeting.infraestructure.ai.AssistantInputValidator;
import dio.budgeting.infraestructure.ai.AssistantPromptTelemetry;
import dio.budgeting.infraestructure.ai.AssistantRateLimitedException;
import dio.budgeting.infraestructure.ai.AssistantSemanticRejectionException;
import dio.budgeting.infraestructure.ai.InterpretationStatus;
import dio.budgeting.infraestructure.ai.RateLimitDecision;
import dio.budgeting.infraestructure.ai.TransactionAssistant;
import dio.budgeting.infraestructure.http.request.TransactionRequest;
import dio.budgeting.infraestructure.http.response.InterpretResponse;
import dio.budgeting.infraestructure.http.response.TransactionHistoryHttpResponse;
import dio.budgeting.infraestructure.http.response.TransactionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

    private final TransactionService transactionService;
    private final TransactionAssistant transactionAssistant;
    private final AiInterpretRateLimiter aiInterpretRateLimiter;
    private final InterpretProperties interpretProperties;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public TransactionController(TransactionService transactionService,
                                  TransactionAssistant transactionAssistant,
                                  AiInterpretRateLimiter aiInterpretRateLimiter,
                                  InterpretProperties interpretProperties,
                                  AuthenticatedUserProvider authenticatedUserProvider) {
        this.transactionService = transactionService;
        this.transactionAssistant = transactionAssistant;
        this.aiInterpretRateLimiter = aiInterpretRateLimiter;
        this.interpretProperties = interpretProperties;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse createTransaction(@RequestBody TransactionRequest request) {
        var transaction = transactionService.create(request.toInput());
        return TransactionResponse.from(transaction);
    }

    @PutMapping("/{id}")
    public TransactionResponse updateTransaction(@PathVariable Long id, @RequestBody TransactionRequest request) {
        var transaction = transactionService.update(id, request.toInput());
        return TransactionResponse.from(transaction);
    }

    @GetMapping
    public TransactionHistoryHttpResponse findHistory(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "category", required = false) Category category) {
        var filters = new TransactionHistoryFilters(
                Optional.ofNullable(from).map(localDate -> localDate.atStartOfDay(ZoneOffset.UTC).toInstant()),
                Optional.ofNullable(to).map(localDate -> localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()),
                Optional.ofNullable(category)
        );
        return TransactionHistoryHttpResponse.from(transactionService.findHistory(filters));
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
    public ResponseEntity<InterpretResponse> interpret(@RequestBody InterpretRequest request) {
        Instant started = Instant.now();
        String prompt = request.prompt();
        try {
            AssistantInputValidator.validatePrompt(prompt, interpretProperties.minPromptLength(), interpretProperties.maxPromptLength());
        } catch (RuntimeException exception) {
            logInterpretAttempt(prompt, started, "validation_error");
            throw exception;
        }

        String rateLimitIdentity = "user:" + authenticatedUserProvider.requireCurrentUserId();
        RateLimitDecision rateLimit = aiInterpretRateLimiter.check(rateLimitIdentity);
        if (!rateLimit.allowed()) {
            logInterpretAttempt(prompt, started, "rate_limited");
            throw new AssistantRateLimitedException("Too many interpretation requests", rateLimit.retryAfterSeconds());
        }

        var result = transactionAssistant.interpret(prompt);
        if (result.status() == InterpretationStatus.OUT_OF_SCOPE) {
            throw new AssistantSemanticRejectionException("Prompt is not a personal expense");
        }

        HttpHeaders headers = new HttpHeaders();
        applyRateLimitHeaders(headers, rateLimit);
        return ResponseEntity.ok().headers(headers).body(InterpretResponse.from(result));
    }

    private void applyRateLimitHeaders(HttpHeaders headers, RateLimitDecision rateLimit) {
        headers.add("RateLimit-Limit", String.valueOf(interpretProperties.rateLimit().requestsPerMinute()));
        headers.add("RateLimit-Remaining", String.valueOf(rateLimit.remaining()));
        headers.add("RateLimit-Reset", String.valueOf(rateLimit.resetEpochSeconds()));
    }

    private void logInterpretAttempt(String prompt, Instant started, String outcome) {
        AssistantPromptTelemetry.logCompletion(log, prompt, started, Instant.now(), outcome);
    }
}
