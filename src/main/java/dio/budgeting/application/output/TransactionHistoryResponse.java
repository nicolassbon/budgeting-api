package dio.budgeting.application.output;

import java.util.List;

public record TransactionHistoryResponse(
        List<TransactionOutput> items,
        long totalAmountCents,
        double totalAmount,
        long transactionCount
) {
}
