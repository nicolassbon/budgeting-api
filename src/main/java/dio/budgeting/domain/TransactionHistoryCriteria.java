package dio.budgeting.domain;

import java.time.Instant;
import java.util.Optional;

public record TransactionHistoryCriteria(
        Long ownerId,
        Optional<Instant> from,
        Optional<Instant> to,
        Optional<Category> category
) {
}
