package dio.budgeting.domain;

import java.util.List;

public record DashboardAggregate(
        long totalAmountCents,
        long transactionCount,
        List<CategoryAggregate> categoryAggregates
) {
    public record CategoryAggregate(Category category, long totalAmountCents, long transactionCount) {
    }
}

