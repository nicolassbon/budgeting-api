package dio.budgeting.application.output;

import dio.budgeting.domain.DashboardAggregate;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CategoryTotalResponse(
        String category,
        long totalAmountCents,
        double totalAmount,
        long transactionCount
) {
    public static CategoryTotalResponse from(DashboardAggregate.CategoryAggregate aggregate) {
        return new CategoryTotalResponse(
                aggregate.category().name(),
                aggregate.totalAmount(),
                BigDecimal.valueOf(aggregate.totalAmount()).setScale(2, RoundingMode.HALF_UP).doubleValue(),
                aggregate.transactionCount()
        );
    }
}
