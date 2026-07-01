package dio.budgeting.infraestructure.http;

import dio.budgeting.BudgetingApplication;
import dio.budgeting.infraestructure.ai.InterpretationResult;
import dio.budgeting.infraestructure.ai.InterpretationStatus;
import dio.budgeting.infraestructure.ai.TransactionAssistant;
import dio.budgeting.domain.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.YearMonth;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(classes = BudgetingApplication.class, properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.docker.compose.enabled=false",
        "ai.interpret.rate-limit.requests-per-minute=1"
})
@Testcontainers
class SecurityEndpointIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("budgeting")
            .withUsername("app")
            .withPassword("app");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @MockitoBean
    private OpenAiChatModel openAiChatModel;

    @MockitoBean
    private TranscriptionModel transcriptionModel;

    @MockitoBean
    private TextToSpeechModel textToSpeechModel;

    @MockitoBean
    private TransactionAssistant transactionAssistant;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldRejectAnonymousProtectedPostAndMultipartEndpoints() throws Exception {
        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson("Anonymous groceries")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/transactions/ai")
                        .file(audioFile())
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/transactions/interpret")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"latte and bread"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/chat-client").param("prompt", "hello"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/chat-model").param("prompt", "hello"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/transcribe")
                        .file(audioFile())
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/sinthesize")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"hello world"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAnonymousDashboardAndHistoryEndpoints() throws Exception {
        mockMvc.perform(get("/dashboard/spending"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/transactions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/transactions")
                        .param("from", "2026-03-01")
                        .param("to", "2026-03-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateHttpOnlySessionCookieOnLogin() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, "secret-password")))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("JSESSIONID", true));
    }

    @Test
    void shouldScopeTransactionListingToAuthenticatedOwnerWithTwoUsers() throws Exception {
        MockHttpSession aliceSession = registerAndLogin(uniqueEmail());
        MockHttpSession bobSession = registerAndLogin(uniqueEmail());
        String aliceDescription = "Alice groceries %s".formatted(UUID.randomUUID());
        String bobDescription = "Bob groceries %s".formatted(UUID.randomUUID());

        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(aliceDescription)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(aliceDescription));

        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(bobSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(bobDescription)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(bobDescription));

        String aliceList = mockMvc.perform(get("/transactions/COMIDA")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(aliceList).contains(aliceDescription);
        assertThat(aliceList).doesNotContain(bobDescription);
    }

    @Test
    void shouldScopeHistoryEndpointToAuthenticatedOwnerWithTwoUsers() throws Exception {
        MockHttpSession aliceSession = registerAndLogin(uniqueEmail());
        MockHttpSession bobSession = registerAndLogin(uniqueEmail());
        String aliceDescription = "Alice history %s".formatted(UUID.randomUUID());
        String bobDescription = "Bob history %s".formatted(UUID.randomUUID());

        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(aliceDescription)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(bobSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(bobDescription)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/transactions")
                        .session(aliceSession)
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.description == '%s')]".formatted(aliceDescription)).exists())
                .andExpect(jsonPath("$.items[?(@.description == '%s')]".formatted(bobDescription)).isEmpty())
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.totalAmountCents").value(1250));

        mockMvc.perform(get("/transactions")
                        .session(aliceSession)
                        .param("category", "COMIDA")
                        .param("from", "2026-01-01")
                        .param("to", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.description == '%s')]".formatted(aliceDescription)).exists())
                .andExpect(jsonPath("$.items[?(@.description == '%s')]".formatted(bobDescription)).isEmpty())
                .andExpect(jsonPath("$.transactionCount").value(1));
    }

    @Test
    void shouldReturnAllOwnerHistoryWhenNoFiltersProvided() throws Exception {
        MockHttpSession aliceSession = registerAndLogin(uniqueEmail());
        MockHttpSession bobSession = registerAndLogin(uniqueEmail());
        String aliceOldDescription = "Alice old history %s".formatted(UUID.randomUUID());
        String aliceCurrentDescription = "Alice current history %s".formatted(UUID.randomUUID());
        String bobOldDescription = "Bob old history %s".formatted(UUID.randomUUID());

        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJsonWithAmountAndDate(aliceOldDescription, 1250, "2020-01-15T10:00:00Z")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJsonWithAmountAndDate(aliceCurrentDescription, 2750, "2026-06-20T12:00:00Z")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(bobSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJsonWithAmountAndDate(bobOldDescription, 9999, "2020-01-15T10:00:00Z")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/transactions")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.description == '%s')]".formatted(aliceOldDescription)).exists())
                .andExpect(jsonPath("$.items[?(@.description == '%s')]".formatted(aliceCurrentDescription)).exists())
                .andExpect(jsonPath("$.items[?(@.description == '%s')]".formatted(bobOldDescription)).isEmpty())
                .andExpect(jsonPath("$.transactionCount").value(2))
                .andExpect(jsonPath("$.totalAmountCents").value(4000));
    }

    @Test
    void shouldScopeDashboardSummaryToAuthenticatedOwner() throws Exception {
        MockHttpSession aliceSession = registerAndLogin(uniqueEmail());
        MockHttpSession bobSession = registerAndLogin(uniqueEmail());

        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJsonWithAmount("Alice groceries", 1000)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(bobSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJsonWithAmount("Bob groceries", 9999)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/dashboard/spending")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountCents").value(1000))
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.topCategories[0].totalAmountCents").value(1000))
                .andExpect(jsonPath("$.topCategories[0].category").value("COMIDA"));

        mockMvc.perform(get("/dashboard/spending")
                        .session(bobSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountCents").value(9999))
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.topCategories[0].totalAmountCents").value(9999));
    }

    @Test
    void shouldExcludeOutOfPeriodTransactionsFromDashboardSummary() throws Exception {
        MockHttpSession aliceSession = registerAndLogin(uniqueEmail());
        MockHttpSession bobSession = registerAndLogin(uniqueEmail());
        YearMonth currentMonth = YearMonth.now(Clock.systemUTC());
        String currentPeriodDate = currentMonth.atDay(15).atTime(12, 0).toInstant(ZoneOffset.UTC).toString();
        String previousPeriodDate = currentMonth.minusMonths(1)
                .atDay(15)
                .atTime(12, 0)
                .toInstant(ZoneOffset.UTC)
                .toString();

        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJsonWithCategoryAmountAndDate(
                                "Alice previous month transport",
                                "TRANSPORTE",
                                9000,
                                previousPeriodDate)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(aliceSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJsonWithCategoryAmountAndDate(
                                "Alice current month groceries",
                                "COMIDA",
                                2500,
                                currentPeriodDate)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(bobSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJsonWithCategoryAmountAndDate(
                                "Bob current month groceries",
                                "COMIDA",
                                7000,
                                currentPeriodDate)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/dashboard/spending")
                        .session(aliceSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmountCents").value(2500))
                .andExpect(jsonPath("$.transactionCount").value(1))
                .andExpect(jsonPath("$.topCategories.length()").value(1))
                .andExpect(jsonPath("$.topCategories[0].category").value("COMIDA"))
                .andExpect(jsonPath("$.topCategories[0].totalAmountCents").value(2500))
                .andExpect(jsonPath("$.topCategories[0].transactionCount").value(1));
    }

    @Test
    void shouldDefaultCreatedTransactionToCurrentTimestampWhenDateOmitted() throws Exception {
        MockHttpSession session = registerAndLogin(uniqueEmail());

        Instant before = Instant.now();
        mockMvc.perform(post("/transactions")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "No-date groceries",
                                  "category": "COMIDA",
                                  "amount": 1234
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").isNotEmpty());
        Instant after = Instant.now();

        String response = mockMvc.perform(get("/transactions")
                        .session(session)
                        .param("from", before.minusSeconds(60).toString().substring(0, 10))
                        .param("to", after.plusSeconds(60).toString().substring(0, 10)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).contains("No-date groceries");
    }

    @Test
    void shouldPreserveAuthenticatedAssistantAndAiEndpointContractsThroughSecurity() throws Exception {
        MockHttpSession session = registerAndLogin(uniqueEmail());
        when(chatClient.prompt().user("hello").call().content()).thenReturn("chat-client-response");
        when(openAiChatModel.call("hello")).thenReturn("chat-model-response");
        when(transcriptionModel.transcribe(any())).thenReturn("transcribed-text");
        when(textToSpeechModel.call("hello world")).thenReturn("mp3-bytes".getBytes());
        when(transactionAssistant.transcribe(any())).thenReturn(ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mp3"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("audio.mp3").build().toString())
                .body(new ByteArrayResource("transaction-audio".getBytes())));
        when(transactionAssistant.interpret("latte and bread"))
                .thenReturn(new InterpretationResult(InterpretationStatus.OK, "Coffee and bread", 2300L, Category.COMIDA));

        mockMvc.perform(get("/api/chat-client")
                        .session(session)
                        .param("prompt", "hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("chat-client-response"));

        mockMvc.perform(get("/api/chat-model")
                        .session(session)
                        .param("prompt", "hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("chat-model-response"));

        mockMvc.perform(multipart("/api/transcribe")
                        .file(audioFile())
                        .with(csrf())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("transcribed-text"));

        mockMvc.perform(post("/api/sinthesize")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text":"hello world"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mp3"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"audio.mp3\""))
                .andExpect(content().bytes("mp3-bytes".getBytes()));

        mockMvc.perform(multipart("/transactions/ai")
                        .file(audioFile())
                        .with(csrf())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/mp3"))
                .andExpect(content().bytes("transaction-audio".getBytes()));

        mockMvc.perform(post("/transactions/interpret")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"latte and bread"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Coffee and bread"))
                .andExpect(jsonPath("$.amount").value(2300))
                .andExpect(jsonPath("$.category").value("COMIDA"))
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void shouldNotResetInterpretRateLimitWhenSameUserGetsNewSession() throws Exception {
        String email = uniqueEmail();
        MockHttpSession firstSession = registerAndLogin(email);
        when(transactionAssistant.interpret("latte and bread"))
                .thenReturn(new InterpretationResult(InterpretationStatus.OK, "Coffee and bread", 2300L, Category.COMIDA));

        mockMvc.perform(post("/transactions/interpret")
                        .with(csrf())
                        .session(firstSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"latte and bread"}
                                """))
                .andExpect(status().isOk());

        MockHttpSession renewedSession = login(email);

        mockMvc.perform(post("/transactions/interpret")
                        .with(csrf())
                        .session(renewedSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"latte and bread"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.error").value("assistant_rate_limited"));
    }

    private MockHttpSession registerAndLogin(String email) throws Exception {
        register(email);
        return login(email);
    }

    private MockHttpSession login(String email) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, "secret-password")))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, "secret-password")))
                .andExpect(status().isCreated());
    }

    private static MockMultipartFile audioFile() {
        return new MockMultipartFile("file", "audio.wav", "audio/wav", "audio".getBytes());
    }

    private static String uniqueEmail() {
        return "user-%s@example.com".formatted(UUID.randomUUID());
    }

    private static String authJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private static String transactionJson(String description) {
        return """
                {
                  "description": "%s",
                  "category": "COMIDA",
                  "amount": 1250
                }
                """.formatted(description);
    }

    private static String transactionJsonWithAmount(String description, long amount) {
        return """
                {
                  "description": "%s",
                  "category": "COMIDA",
                  "amount": %d
                }
                """.formatted(description, amount);
    }

    private static String transactionJsonWithAmountAndDate(String description, long amount, String date) {
        return """
                {
                  "description": "%s",
                  "category": "COMIDA",
                  "amount": %d,
                  "date": "%s"
                }
                """.formatted(description, amount, date);
    }

    private static String transactionJsonWithCategoryAmountAndDate(String description,
                                                                   String category,
                                                                   long amount,
                                                                   String date) {
        return """
                {
                  "description": "%s",
                  "category": "%s",
                  "amount": %d,
                  "date": "%s"
                }
                """.formatted(description, category, amount, date);
    }
}
