package dio.budgeting.application.output;

import dio.budgeting.domain.DashboardAggregate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record DashboardSummaryResponse(
        PeriodResponse period,
        long totalAmountCents,
        double totalAmount,
        long transactionCount,
        List<CategoryTotalResponse> topCategories
) {
    public static DashboardSummaryResponse from(PeriodResponse period, DashboardAggregate aggregate) {
        var categories = aggregate.categoryAggregates().stream()
                .map(CategoryTotalResponse::from)
                .toList();
        return new DashboardSummaryResponse(
                period,
                aggregate.totalAmountCents(),
                BigDecimal.valueOf(aggregate.totalAmountCents() / 100.0).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                aggregate.transactionCount(),
                categories
        );
    }
}

