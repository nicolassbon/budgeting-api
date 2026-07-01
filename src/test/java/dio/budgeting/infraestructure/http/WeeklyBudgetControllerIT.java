package dio.budgeting.infraestructure.http;

import dio.budgeting.BudgetingApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(classes = BudgetingApplication.class, properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.docker.compose.enabled=false"
})
@Testcontainers
class WeeklyBudgetControllerIT {
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldReturnNullWhenWeeklyBudgetIsMissing() throws Exception {
        MockHttpSession session = loginNewUser();

        mockMvc.perform(get("/auth/me/weekly-budget").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").isEmpty());
    }

    @Test
    void shouldPersistAndReadWeeklyBudgetAmount() throws Exception {
        MockHttpSession session = loginNewUser();

        mockMvc.perform(put("/auth/me/weekly-budget")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":12500.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(12500.50));

        mockMvc.perform(get("/auth/me/weekly-budget").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(12500.50));
    }

    @Test
    void shouldClearWeeklyBudgetToNull() throws Exception {
        MockHttpSession session = loginNewUser();

        mockMvc.perform(put("/auth/me/weekly-budget")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":300.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(300.00));

        mockMvc.perform(put("/auth/me/weekly-budget")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").isEmpty());

        mockMvc.perform(get("/auth/me/weekly-budget").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").isEmpty());
    }

    @Test
    void shouldRejectAnonymousWeeklyBudgetRequests() throws Exception {
        mockMvc.perform(get("/auth/me/weekly-budget"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/auth/me/weekly-budget")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidWeeklyBudgetPayloadWithoutChangingStoredAmount() throws Exception {
        MockHttpSession session = loginNewUser();

        mockMvc.perform(put("/auth/me/weekly-budget")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":750.25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(750.25));

        mockMvc.perform(put("/auth/me/weekly-budget")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"not-a-number\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/auth/me/weekly-budget").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(750.25));
    }

    @Test
    void shouldRejectNegativeWeeklyBudgetWithoutChangingStoredAmount() throws Exception {
        MockHttpSession session = loginNewUser();

        mockMvc.perform(put("/auth/me/weekly-budget")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":250.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(250.00));

        mockMvc.perform(put("/auth/me/weekly-budget")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-1}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/auth/me/weekly-budget").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(250.00));
    }

    private MockHttpSession loginNewUser() throws Exception {
        String email = uniqueEmail();
        String password = "secret-password";

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, password)))
                .andExpect(status().isCreated());

        var login = mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private static String uniqueEmail() {
        return "weekly-budget-%s@example.com".formatted(UUID.randomUUID());
    }

    private static String authJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }
}
