package dio.budgeting;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureDocumentationGuidanceTest {

    private static final Path README_PATH = Path.of("README.md");
    private static final Path OPENSPEC_CONFIG_PATH = Path.of("openspec/config.yaml");
    private static final Path ARCHITECTURE_SPEC_PATH = Path.of("openspec/specs/architecture-guidance/spec.md");
    private static final Path ASSISTANT_FACADE_PATH = Path.of("src/main/java/dio/budgeting/infraestructure/ai/TransactionAssistantFacade.java");
    private static final Path DOMAIN_PATH = Path.of("src/main/java/dio/budgeting/domain");

    @Test
    void shouldNamePragmaticLayeredArchitectureAsTheOfficialMvpStyle() throws IOException {
        String readme = readFile(README_PATH);

        assertThat(readme).contains("pragmatic Layered Architecture");
        assertThat(readme).contains("strict Hexagonal Architecture and full Clean Architecture are out of scope for this MVP");
        assertThat(readme).doesNotContain("arquitectura limpia / hexagonal");
    }

    @Test
    void shouldDescribeCurrentLayerResponsibilitiesAndManualFallbackInReadme() throws IOException {
        String readme = readFile(README_PATH);

        assertThat(readme).contains("Los controladores deben seguir siendo adapters finos de HTTP/transporte y no dueños de reglas de negocio");
        assertThat(readme).contains("`domain/`: core business models, invariants, and repository contracts");
        assertThat(readme).contains("`application/`: use-case orchestration, transaction boundaries, and user-scoped operations");
        assertThat(readme).contains("`infraestructure/`: HTTP, persistence, security, and framework adapters");
        assertThat(readme).contains("`infraestructure/ai/`: AI-facing orchestration owned by the infrastructure edge");
        assertThat(readme).contains("Manual transaction creation remains available even if AI flows fail or are not used");
        assertThat(readme).contains("manual editing is part of the target MVP scope but must be introduced through an explicit backend change");
    }

    @Test
    void shouldAlignOpenSpecContextAndChangeSpecWithLayeredGuidance() throws IOException {
        String config = readFile(OPENSPEC_CONFIG_PATH);
        String architectureSpec = readFile(ARCHITECTURE_SPEC_PATH);

        assertThat(config).contains("Architecture: pragmatic Layered Architecture for the MVP");
        assertThat(config).contains("avoid extra abstraction that does not provide a clear MVP benefit");
        assertThat(architectureSpec).contains("pragmatic Layered Architecture with clean boundaries");
        assertThat(architectureSpec).contains("`infraestructure` spelling and current endpoint compatibility SHALL remain explicit constraints");
    }

    @Test
    void shouldDocumentTransactionAssistantFacadeAsInfrastructureOwnedAiOrchestrator() throws IOException {
        String source = readFile(ASSISTANT_FACADE_PATH);

        assertThat(source).contains("Infrastructure-owned AI orchestrator");
        assertThat(source).contains("interpretation result is produced as a draft and must not persist data before user confirmation");
    }

    @Test
    void shouldKeepConfirmationRuleExplicitForFutureRefactorsAndEndpointDocs() throws IOException {
        String readme = readFile(README_PATH);
        String architectureSpec = readFile(ARCHITECTURE_SPEC_PATH);

        assertThat(readme).contains("`POST /transactions/ai`: conserva el flujo asistido actual de transcripción, tool calling y respuesta de audio; para un borrador con confirmación previa usá `POST /transactions/interpret`");
        assertThat(readme).contains("Cualquier refactor futuro del flujo IA debe preservar la confirmación antes del guardado como una regla obligatoria");
        assertThat(architectureSpec).contains("preserve confirmation-before-save as a mandatory business rule");
    }

    @Test
    void shouldKeepDomainPackageFreeFromFrameworkImports() throws IOException {
        try (Stream<Path> paths = Files.walk(DOMAIN_PATH)) {
            List<Path> javaFiles = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();

            assertThat(javaFiles).isNotEmpty();

            for (Path javaFile : javaFiles) {
                String source = readFile(javaFile);
                assertThat(source)
                        .doesNotContain("import org.springframework")
                        .doesNotContain("import jakarta.persistence")
                        .doesNotContain("import org.springframework.ai")
                        .doesNotContain("import org.springframework.security");
            }
        }
    }

    private String readFile(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
