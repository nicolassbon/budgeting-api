package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import org.springframework.ai.tool.annotation.ToolParam;

public record PersistTransactionInput(@ToolParam(description = "Descripción del gasto") String description,
                                      @ToolParam(description = "Valor del gasto (en centavos)") long amount,
                                      @ToolParam(description = "Categoria de una transacción") Category category) {
}
