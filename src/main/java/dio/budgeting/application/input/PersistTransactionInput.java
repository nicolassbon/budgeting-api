package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.Instant;

public record PersistTransactionInput(
        @ToolParam(description = "Descripción del gasto") String description,
        @ToolParam(description = "Valor del gasto (en centavos)") long amount,
        @ToolParam(description = "Categoria de una transacción") Category category,
        @ToolParam(description = "Fecha y hora opcional de la transacción en formato ISO-8601 (UTC)") Instant occurredAt
) {
    public static PersistTransactionInput of(String description, long amount, Category category) {
        return new PersistTransactionInput(description, amount, category, null);
    }

    public static PersistTransactionInput of(String description, long amount, Category category, Instant occurredAt) {
        return new PersistTransactionInput(description, amount, category, occurredAt);
    }
}
