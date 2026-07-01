package dio.budgeting.infraestructure.http;

import dio.budgeting.BudgetingApplication;
import dio.budgeting.application.auth.PasswordResetEmail;
import dio.budgeting.application.auth.PasswordResetMailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(classes = BudgetingApplication.class, properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.docker.compose.enabled=false"
})
@Import(AuthControllerTest.PasswordResetMailTestConfiguration.class)
@Testcontainers
class AuthControllerTest {
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

    @Autowired
    private CapturingPasswordResetMailSender passwordResetMailSender;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        passwordResetMailSender.clear();
        mockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldRegisterLoginExposeCurrentUserAndLogoutSession() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, "secret-password")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"));

        var login = mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, "secret-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn();

        var session = login.getRequest().getSession(false);

        mockMvc.perform(get("/auth/me").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/auth/logout")
                        .with(csrf())
                        .session((MockHttpSession) session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/auth/me").session((MockHttpSession) session))
                .andExpect(status().isUnauthorized());
	}

    @Test
    void shouldAcceptForgotPasswordWithoutRevealingAccountsAndSendOnlyKnownUserResetLink() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, "secret-password")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotPasswordJson(" " + email.toUpperCase() + " ")))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotPasswordJson("missing-" + email)))
                .andExpect(status().isAccepted());

        assertThat(passwordResetMailSender.sentEmails()).hasSize(1);
        PasswordResetEmail sentEmail = passwordResetMailSender.sentEmails().getFirst();
        assertThat(sentEmail.to()).isEqualTo(email);
        assertThat(extractToken(sentEmail.resetLink())).isNotBlank();
    }

    @Test
    void shouldResetPasswordOnceWithValidToken() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, "secret-password")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/forgot-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotPasswordJson(email)))
                .andExpect(status().isAccepted());
        String token = extractToken(passwordResetMailSender.sentEmails().getFirst().resetLink());

        mockMvc.perform(post("/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPasswordJson(token, "new-secret-password")))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, "new-secret-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/auth/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPasswordJson(token, "another-secret-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("reset_token_invalid"));
    }

    @Test
    void shouldRejectDuplicateEmailAndBadCredentials() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, "secret-password")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, "other-password")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("duplicate_email"));

        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(email, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication_failed"));
    }

    @Test
    void shouldRejectAnonymousProtectedRequests() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/transactions/COMIDA"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/chat-client").param("prompt", "hello"))
                .andExpect(status().isUnauthorized());
    }

    private static String uniqueEmail() {
        return "user-%s@example.com".formatted(UUID.randomUUID());
    }

    private static String authJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private static String forgotPasswordJson(String email) {
        return """
                {"email":"%s"}
                """.formatted(email);
    }

    private static String resetPasswordJson(String token, String newPassword) {
        return """
                {"token":"%s","newPassword":"%s"}
                """.formatted(token, newPassword);
    }

    private static String extractToken(String resetLink) {
        String query = URI.create(resetLink).getQuery();
        for (String parameter : query.split("&")) {
            String[] parts = parameter.split("=", 2);
            if (parts.length == 2 && parts[0].equals("token")) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Reset token not found in link");
    }

    @TestConfiguration
    static class PasswordResetMailTestConfiguration {
        @Bean
        @Primary
        CapturingPasswordResetMailSender passwordResetMailSender() {
            return new CapturingPasswordResetMailSender();
        }
    }

    static class CapturingPasswordResetMailSender implements PasswordResetMailSender {
        private final List<PasswordResetEmail> sentEmails = new ArrayList<>();

        @Override
        public void send(PasswordResetEmail email) {
            sentEmails.add(email);
        }

        List<PasswordResetEmail> sentEmails() {
            return sentEmails;
        }

        void clear() {
            sentEmails.clear();
        }
    }
}
