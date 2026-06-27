package dio.budgeting.infraestructure.http;

import dio.budgeting.application.TransactionService;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionControllerTest {

    private final TransactionService transactionService = mock(TransactionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws IOException {
        var chatClientBuilder = mock(ChatClient.Builder.class, org.mockito.Answers.RETURNS_SELF);
        when(chatClientBuilder.build()).thenReturn(mock(ChatClient.class));

        var controller = new TransactionController(
                transactionService,
                mock(TranscriptionModel.class),
                chatClientBuilder,
                new ByteArrayResource("system prompt".getBytes()),
                mock(TextToSpeechModel.class)
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
}
