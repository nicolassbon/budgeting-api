package dio.budgeting.infraestructure.http;

import dio.budgeting.assistant.TransactionAssistant;
import dio.budgeting.assistant.TransactionDraft;
import dio.budgeting.application.TransactionService;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionControllerTest {

    private final TransactionService transactionService = mock(TransactionService.class);
    private final TransactionAssistant transactionAssistant = mock(TransactionAssistant.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(transactionAssistant.transcribe(org.mockito.ArgumentMatchers.any())).thenReturn(
                ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("audio/mp3"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename("audio.mp3").build().toString())
                        .body(new ByteArrayResource("audio-bytes".getBytes()))
        );
        when(transactionAssistant.interpret("latte and bread"))
                .thenReturn(new TransactionDraft("Coffee and bread", 2300L, Category.GROCERIES));

        var controller = new TransactionController(transactionService, transactionAssistant);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new dio.budgeting.assistant.AssistantExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateTransactionWithStableHttpContract() throws Exception {
        when(transactionService.create(new dio.budgeting.application.input.PersistTransactionInput(
                "Supermarket",
                1250L,
                Category.GROCERIES
        ))).thenReturn(new TransactionOutput("1", "Supermarket", "GROCERIES", 1250.0));

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Supermarket",
                                  "category": "GROCERIES",
                                  "amount": 1250
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.description").value("Supermarket"))
                .andExpect(jsonPath("$.category").value("GROCERIES"))
                .andExpect(jsonPath("$.amount").value(1250.0));
    }

    @Test
    void shouldReturnEmptyListForCategoryWithoutChangingResponseShape() throws Exception {
        when(transactionService.findAllByCategory(Category.PHARMA)).thenReturn(List.of());

        mockMvc.perform(get("/transactions/{category}", Category.PHARMA))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void shouldListTransactionsWithStableHttpContract() throws Exception {
        when(transactionService.findAllByCategory(Category.PHARMA)).thenReturn(List.of(
                new TransactionOutput("10", "Pharmacy", "PHARMA", 450.0)
        ));

        mockMvc.perform(get("/transactions/{category}", Category.PHARMA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("10"))
                .andExpect(jsonPath("$[0].description").value("Pharmacy"))
                .andExpect(jsonPath("$[0].category").value("PHARMA"))
                .andExpect(jsonPath("$[0].amount").value(450.0));
    }

    @Test
    void shouldKeepAiEndpointAudioContractStable() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/transactions/ai")
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
                .andExpect(jsonPath("$.category").value("GROCERIES"));
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
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/transactions/ai")
                        .file(new MockMultipartFile("file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, "audio".getBytes())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("assistant_validation_error"))
                .andExpect(jsonPath("$.message").value("Audio file content type must be audio/* or application/octet-stream"));
    }

    @Test
    void shouldReturnBadGatewayWhenAssistantTranscriptionFails() throws Exception {
        when(transactionAssistant.transcribe(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new dio.budgeting.assistant.AssistantIntegrationException(
                        "Failed to transcribe the provided audio",
                        new IllegalStateException("openai down")
                ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/transactions/ai")
                        .file(new MockMultipartFile("file", "audio.wav", MediaType.APPLICATION_OCTET_STREAM_VALUE, "audio".getBytes())))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("assistant_integration_error"))
                .andExpect(jsonPath("$.message").value("Failed to transcribe the provided audio"));
    }
}
