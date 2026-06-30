package dio.budgeting.infraestructure.http.response;

import dio.budgeting.application.output.TransactionHistoryResponse;

import java.util.List;

public record TransactionHistoryHttpResponse(
        List<TransactionResponse> items,
        long totalAmountCents,
        double totalAmount,
        long transactionCount
) {
    public static TransactionHistoryHttpResponse from(TransactionHistoryResponse response) {
        return new TransactionHistoryHttpResponse(
                response.items().stream()
                        .map(TransactionResponse::from)
                        .toList(),
                response.totalAmountCents(),
                response.totalAmount(),
                response.transactionCount()
        );
    }
}
