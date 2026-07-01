package dio.budgeting.infraestructure.http.request;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;

import java.time.Instant;

public record TransactionRequest(String description, Category category, long amount, Instant date) {
    public PersistTransactionInput toInput() {
        return PersistTransactionInput.of(description, amount, category, date);
    }
}

