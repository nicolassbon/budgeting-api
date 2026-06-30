package dio.budgeting.application;

import dio.budgeting.application.output.DashboardSummaryResponse;
import dio.budgeting.application.output.PeriodResponse;
import dio.budgeting.application.security.AuthenticatedUserProvider;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

@Service
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;
    private final Clock clock;

    public DashboardService(TransactionRepository transactionRepository,
                            AuthenticatedUserProvider authenticatedUserProvider,
                            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
        this.clock = clock;
    }

    public DashboardSummaryResponse currentMonthSummary() {
        YearMonth currentMonth = YearMonth.now(clock.withZone(ZoneOffset.UTC));
        LocalDate fromDate = currentMonth.atDay(1);
        LocalDate toDateExclusive = currentMonth.plusMonths(1).atDay(1);

        Instant from = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = toDateExclusive.atStartOfDay(ZoneOffset.UTC).toInstant();

        Long ownerId = authenticatedUserProvider.requireCurrentUserId();
        var aggregate = transactionRepository.aggregateByOwnerAndPeriod(ownerId, from, to);
        return DashboardSummaryResponse.from(new PeriodResponse(fromDate, toDateExclusive), aggregate);
    }
}
