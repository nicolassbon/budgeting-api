package dio.budgeting.domain;

import java.time.Instant;

public record TransactionHistoryEntry(
        TransactionId id,
        String description,
        long amount,
        Category category,
        Instant occurredAt
) {
}
