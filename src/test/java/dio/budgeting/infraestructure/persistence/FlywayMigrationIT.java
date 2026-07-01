package dio.budgeting.infraestructure.persistence;

import dio.budgeting.BudgetingApplication;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class FlywayMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("budgeting")
            .withUsername("app")
            .withPassword("app");

    @Test
    void should_applyVersionedMigrationOnStartup_when_schemaHistoryMissing() throws SQLException {
        String databaseName = createDatabase();

        try (ConfigurableApplicationContext context = startBudgetingApplication(databaseName)) {
            assertThat(context.isActive()).isTrue();
        }

        assertThat(readAppliedVersions(databaseName)).containsExactly("1", "2", "3", "4");
        assertThat(readColumns(databaseName)).containsExactly(
                new ColumnState("id", "bigint", "NO", "YES"),
                new ColumnState("description", "character varying", "YES", "NO"),
                new ColumnState("amount", "bigint", "NO", "NO"),
                new ColumnState("category", "character varying", "YES", "NO"),
                new ColumnState("owner_id", "bigint", "YES", "NO"),
                new ColumnState("occurred_at", "timestamp with time zone", "NO", "NO")
        );
        assertThat(readUserColumns(databaseName)).containsExactly(
                new ColumnState("id", "bigint", "NO", "YES"),
                new ColumnState("email", "character varying", "NO", "NO"),
                new ColumnState("password", "character varying", "NO", "NO"),
                new ColumnState("role", "character varying", "NO", "NO")
        );
    }

    @Test
    void should_failStartup_when_schemaHistoryDivergesFromMigrationFiles() throws SQLException {
        String databaseName = createDatabase();

        try (ConfigurableApplicationContext ignored = startBudgetingApplication(databaseName)) {
            assertThat(readAppliedVersions(databaseName)).containsExactly("1", "2", "3", "4");
        }

        assertThatThrownBy(() -> startBudgetingApplication(
                databaseName,
                "spring.flyway.locations=classpath:db/test-migration/divergent"
        ))
                .rootCause()
                .hasMessageContaining("Validate failed")
                .hasMessageContaining("checksum");
    }

    @Test
    void should_notImplicitlyAlterSchema_when_entityAddsFieldWithoutMigration() throws SQLException {
        String databaseName = createDatabase();

        try (ConfigurableApplicationContext ignored = startBudgetingApplication(databaseName)) {
            assertThat(readAppliedVersions(databaseName)).containsExactly("1", "2", "3", "4");
        }

        assertThatThrownBy(() -> startSchemaValidationApplication(databaseName))
                .rootCause()
                .hasMessageContaining("missing column [notes]");

        assertThat(readColumnNames(databaseName)).doesNotContain("notes");
    }

    private static ConfigurableApplicationContext startBudgetingApplication(String databaseName, String... extraProperties) {
        List<String> properties = baseProperties(databaseName);
        properties.addAll(List.of(extraProperties));

        return new SpringApplicationBuilder(BudgetingApplication.class)
                .web(WebApplicationType.NONE)
                .run(properties.stream().map(property -> "--" + property).toArray(String[]::new));
    }

    private static ConfigurableApplicationContext startSchemaValidationApplication(String databaseName) {
        List<String> properties = baseProperties(databaseName);
        properties.add("spring.flyway.enabled=false");
        properties.add("spring.jpa.hibernate.ddl-auto=validate");

        return new SpringApplicationBuilder(SchemaValidationApplication.class)
                .web(WebApplicationType.NONE)
                .run(properties.stream().map(property -> "--" + property).toArray(String[]::new));
    }

    private static List<String> baseProperties(String databaseName) {
        return new ArrayList<>(List.of(
                "spring.datasource.url=" + databaseUrl(databaseName),
                "spring.datasource.username=" + POSTGRESQL.getUsername(),
                "spring.datasource.password=" + POSTGRESQL.getPassword(),
                "spring.datasource.driver-class-name=org.postgresql.Driver",
                "spring.ai.openai.api-key=test-key",
                "spring.docker.compose.enabled=false",
                "spring.main.banner-mode=off"
        ));
    }

    private static String createDatabase() throws SQLException {
        String databaseName = "budgeting_" + UUID.randomUUID().toString().replace("-", "");

        try (var connection = DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(),
                POSTGRESQL.getUsername(),
                POSTGRESQL.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE \"" + databaseName + "\"");
        }

        return databaseName;
    }

    private static String databaseUrl(String databaseName) {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                POSTGRESQL.getHost(),
                POSTGRESQL.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                databaseName
        );
    }

    private static List<String> readAppliedVersions(String databaseName) throws SQLException {
        List<String> versions = new ArrayList<>();

        try (var connection = DriverManager.getConnection(databaseUrl(databaseName), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank")) {
            while (resultSet.next()) {
                versions.add(resultSet.getString("version"));
            }
        }

        return versions;
    }

    private static List<ColumnState> readColumns(String databaseName) throws SQLException {
        List<ColumnState> columns = new ArrayList<>();
        String query = """
                SELECT column_name, data_type, is_nullable, is_identity
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'transaction_entity'
                ORDER BY ordinal_position
                """;

        try (var connection = DriverManager.getConnection(databaseUrl(databaseName), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                columns.add(new ColumnState(
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getString("is_nullable"),
                        resultSet.getString("is_identity")
                ));
            }
        }

        return columns;
    }

    private static List<ColumnState> readUserColumns(String databaseName) throws SQLException {
        return readColumns(databaseName, "app_user");
    }

    private static List<ColumnState> readColumns(String databaseName, String tableName) throws SQLException {
        List<ColumnState> columns = new ArrayList<>();
        String query = """
                SELECT column_name, data_type, is_nullable, is_identity
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = '%s'
                ORDER BY ordinal_position
                """.formatted(tableName);

        try (var connection = DriverManager.getConnection(databaseUrl(databaseName), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                columns.add(new ColumnState(
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getString("is_nullable"),
                        resultSet.getString("is_identity")
                ));
            }
        }

        return columns;
    }

    private static List<String> readColumnNames(String databaseName) throws SQLException {
        return readColumns(databaseName).stream()
                .map(ColumnState::name)
                .toList();
    }

    private record ColumnState(String name, String dataType, String nullable, String identity) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = migrationtest.EvolvedTransactionEntity.class)
    static class SchemaValidationApplication {
    }
}
