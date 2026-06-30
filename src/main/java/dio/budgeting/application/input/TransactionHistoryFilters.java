package dio.budgeting.application.input;

import dio.budgeting.domain.Category;

import java.time.Instant;
import java.util.Optional;

public record TransactionHistoryFilters(
        Optional<Instant> from,
        Optional<Instant> to,
        Optional<Category> category
) {
    public TransactionHistoryFilters(Instant from, Instant to, Optional<Category> category) {
        this(Optional.of(from), Optional.of(to), category);
    }
}
