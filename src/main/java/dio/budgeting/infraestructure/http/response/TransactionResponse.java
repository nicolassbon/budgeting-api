package dio.budgeting.infraestructure.http.response;

import dio.budgeting.application.output.TransactionOutput;

import java.time.Instant;

public record TransactionResponse(String id, String description, String category, double amount, Instant date) {
    public static TransactionResponse from(TransactionOutput transaction) {
        return new TransactionResponse(
                transaction.id(),
                transaction.description(),
                transaction.category(),
                transaction.value(),
                transaction.date()
        );
    }
}

