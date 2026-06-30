package dio.budgeting.infraestructure.http;

import dio.budgeting.assistant.TransactionAssistant;
import dio.budgeting.assistant.AssistantExceptionHandler;
import dio.budgeting.assistant.AssistantIntegrationException;
import dio.budgeting.assistant.TransactionDraft;
import dio.budgeting.application.TransactionService;
import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.input.TransactionHistoryFilters;
import dio.budgeting.application.output.TransactionHistoryResponse;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionControllerTest {

    private final TransactionService transactionService = mock(TransactionService.class);
    private final TransactionAssistant transactionAssistant = mock(TransactionAssistant.class);
    private MockMvc mockMvc;

    private static final Instant FIXED_DATE = Instant.parse("2026-03-15T10:00:00Z");

    @BeforeEach
    void setUp() {
        when(transactionAssistant.transcribe(ArgumentMatchers.any())).thenReturn(
                ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("audio/mp3"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename("audio.mp3").build().toString())
                        .body(new ByteArrayResource("audio-bytes".getBytes()))
        );
        when(transactionAssistant.interpret("latte and bread"))
                .thenReturn(new TransactionDraft("Coffee and bread", 2300L, Category.COMIDA));

        var controller = new TransactionController(transactionService, transactionAssistant);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AssistantExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateTransactionWithStableHttpContract() throws Exception {
        when(transactionService.create(new PersistTransactionInput(
                "Supermarket",
                1250L,
                Category.COMIDA,
                null
        ))).thenReturn(new TransactionOutput("1", "Supermarket", "COMIDA", 1250.0, FIXED_DATE));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Supermarket",
                                  "category": "COMIDA",
                                  "amount": 1250
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.description").value("Supermarket"))
                .andExpect(jsonPath("$.category").value("COMIDA"))
                .andExpect(jsonPath("$.amount").value(1250.0))
                .andExpect(jsonPath("$.date").value("2026-03-15T10:00:00Z"));
    }

    @Test
    void shouldCreateTransactionWithExplicitDate() throws Exception {
        when(transactionService.create(new PersistTransactionInput(
                "Pharmacy",
                450L,
                Category.FARMACIA,
                FIXED_DATE
        ))).thenReturn(new TransactionOutput("2", "Pharmacy", "FARMACIA", 4.50, FIXED_DATE));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Pharmacy",
                                  "category": "FARMACIA",
                                  "amount": 450,
                                  "date": "2026-03-15T10:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("2"))
                .andExpect(jsonPath("$.description").value("Pharmacy"))
                .andExpect(jsonPath("$.category").value("FARMACIA"))
                .andExpect(jsonPath("$.amount").value(4.50))
                .andExpect(jsonPath("$.date").value("2026-03-15T10:00:00Z"));
    }

    @Test
    void shouldReturnEmptyListForCategoryWithoutChangingResponseShape() throws Exception {
        when(transactionService.findAllByCategory(Category.FARMACIA)).thenReturn(List.of());

        mockMvc.perform(get("/transactions/{category}", Category.FARMACIA))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void shouldListTransactionsWithStableHttpContract() throws Exception {
        when(transactionService.findAllByCategory(Category.FARMACIA)).thenReturn(List.of(
                new TransactionOutput("10", "Pharmacy", "FARMACIA", 450.0, FIXED_DATE)
        ));

        mockMvc.perform(get("/transactions/{category}", Category.FARMACIA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("10"))
                .andExpect(jsonPath("$[0].description").value("Pharmacy"))
                .andExpect(jsonPath("$[0].category").value("FARMACIA"))
                .andExpect(jsonPath("$[0].amount").value(450.0))
                .andExpect(jsonPath("$[0].date").value("2026-03-15T10:00:00Z"));
    }

    @Test
    void shouldListHistoryForAuthenticatedOwner() throws Exception {
        when(transactionService.findHistory(ArgumentMatchers.any(TransactionHistoryFilters.class)))
                .thenReturn(new TransactionHistoryResponse(
                        List.of(
                                new TransactionOutput("11", "Coffee", "COMIDA", 500.0, FIXED_DATE),
                                new TransactionOutput("12", "Subway", "TRANSPORTE", 2250.0, Instant.parse("2026-03-12T08:00:00Z"))
                        ),
                        2750L,
                        2750.0,
                        2L
                ));

        mockMvc.perform(get("/transactions")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("11"))
                .andExpect(jsonPath("$.items[0].description").value("Coffee"))
                .andExpect(jsonPath("$.items[0].category").value("COMIDA"))
                .andExpect(jsonPath("$.items[0].amount").value(500.0))
                .andExpect(jsonPath("$.items[0].date").value("2026-03-15T10:00:00Z"))
                .andExpect(jsonPath("$.items[1].id").value("12"))
                .andExpect(jsonPath("$.items[1].category").value("TRANSPORTE"))
                .andExpect(jsonPath("$.items[1].date").value("2026-03-12T08:00:00Z"))
                .andExpect(jsonPath("$.totalAmountCents").value(2750))
                .andExpect(jsonPath("$.totalAmount").value(2750.0))
                .andExpect(jsonPath("$.transactionCount").value(2));
    }

    @Test
    void shouldListHistoryWithCategoryFilter() throws Exception {
        when(transactionService.findHistory(ArgumentMatchers.any(TransactionHistoryFilters.class)))
                .thenReturn(new TransactionHistoryResponse(
                        List.of(
                                new TransactionOutput("21", "Pharmacy", "FARMACIA", 450.0, FIXED_DATE)
                        ),
                        450L,
                        450.0,
                        1L
                ));

        mockMvc.perform(get("/transactions")
                        .param("category", "FARMACIA")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].category").value("FARMACIA"))
                .andExpect(jsonPath("$.totalAmountCents").value(450))
                .andExpect(jsonPath("$.transactionCount").value(1));
    }

    @Test
    void shouldListHistoryWithoutDateFilters() throws Exception {
        when(transactionService.findHistory(ArgumentMatchers.any(TransactionHistoryFilters.class)))
                .thenReturn(new TransactionHistoryResponse(
                        List.of(new TransactionOutput("31", "Old Coffee", "COMIDA", 1250.0, FIXED_DATE)),
                        1250L,
                        1250.0,
                        1L
                ));

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].description").value("Old Coffee"))
                .andExpect(jsonPath("$.totalAmountCents").value(1250))
                .andExpect(jsonPath("$.transactionCount").value(1));

        ArgumentCaptor<TransactionHistoryFilters> captor = ArgumentCaptor.forClass(TransactionHistoryFilters.class);
        verify(transactionService).findHistory(captor.capture());
        assertThat(captor.getValue().from()).isEmpty();
        assertThat(captor.getValue().to()).isEmpty();
        assertThat(captor.getValue().category()).isEmpty();
    }

    @Test
    void shouldReturnHistoryResponseWithZeroTotalsWhenEmpty() throws Exception {
        when(transactionService.findHistory(ArgumentMatchers.any(TransactionHistoryFilters.class)))
                .thenReturn(new TransactionHistoryResponse(
                        List.of(),
                        0L,
                        0.0,
                        0L
                ));

        mockMvc.perform(get("/transactions")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalAmountCents").value(0))
                .andExpect(jsonPath("$.totalAmount").value(0.0))
                .andExpect(jsonPath("$.transactionCount").value(0));
    }

    @Test
    void shouldKeepAiEndpointAudioContractStable() throws Exception {
        mockMvc.perform(multipart("/transactions/ai")
                        .file(new MockMultipartFile("file", "audio.wav", MediaType.APPLICATION_OCTET_STREAM_VALUE, "audio".getBytes())))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mp3"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"audio.mp3\""))
                .andExpect(content().bytes("audio-bytes".getBytes()));
    }

    @Test
    void shouldKeepInterpretEndpointJsonContractStable() throws Exception {
        mockMvc.perform(post("/transactions/interpret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": "latte and bread"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Coffee and bread"))
                .andExpect(jsonPath("$.amount").value(2300))
                .andExpect(jsonPath("$.category").value("COMIDA"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void shouldReturnBadRequestForBlankInterpretPrompt() throws Exception {
        mockMvc.perform(post("/transactions/interpret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("assistant_validation_error"))
                .andExpect(jsonPath("$.message").value("Prompt must not be blank"));
    }

    @Test
    void shouldReturnBadRequestForUnsupportedAudioType() throws Exception {
        mockMvc.perform(multipart("/transactions/ai")
                        .file(new MockMultipartFile("file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, "audio".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("assistant_validation_error"))
                .andExpect(jsonPath("$.message").value("Audio file content type must be audio/* or application/octet-stream"));
    }

    @Test
    void shouldReturnBadGatewayWhenAssistantTranscriptionFails() throws Exception {
        when(transactionAssistant.transcribe(ArgumentMatchers.any()))
                .thenThrow(new AssistantIntegrationException(
                        "Failed to transcribe the provided audio",
                        new IllegalStateException("openai down")
                ));

        mockMvc.perform(multipart("/transactions/ai")
                        .file(new MockMultipartFile("file", "audio.wav", MediaType.APPLICATION_OCTET_STREAM_VALUE, "audio".getBytes())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("assistant_integration_error"))
                .andExpect(jsonPath("$.message").value("Failed to transcribe the provided audio"));
    }
}
