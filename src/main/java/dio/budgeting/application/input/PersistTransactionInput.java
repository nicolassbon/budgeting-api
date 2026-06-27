package dio.budgeting.application.input;

import dio.budgeting.domain.Category;
import dio.budgeting.infraestructure.http.request.TransactionRequest;
import org.springframework.ai.tool.annotation.ToolParam;

public record PersistTransactionInput(@ToolParam(description = "Descripción del gasto") String description,
                                      @ToolParam(description = "Valor del gasto (en centavos)") long amount,
                                      @ToolParam(description = "Categoria de una transacción") Category category) {
    public static PersistTransactionInput from(TransactionRequest request) {
        return new PersistTransactionInput(request.description(), request.amount(), request.category());
    }
}
