package dio.budgeting.domain;

import java.util.List;

public record DashboardAggregate(
        long totalAmount,
        long transactionCount,
        List<CategoryAggregate> categoryAggregates
) {
    public record CategoryAggregate(Category category, long totalAmount, long transactionCount) {
    }
}
