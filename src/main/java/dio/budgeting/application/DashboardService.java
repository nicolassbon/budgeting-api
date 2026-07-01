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
import java.time.ZoneId;

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

    public DashboardSummaryResponse currentMonthSummary(String timeZoneHeader) {
        ZoneId zoneId;
        try {
            zoneId = (timeZoneHeader != null && !timeZoneHeader.isBlank())
                    ? ZoneId.of(timeZoneHeader)
                    : clock.getZone();
        } catch (Exception e) {
            zoneId = clock.getZone();
        }

        YearMonth currentMonth = YearMonth.now(clock.withZone(zoneId));
        LocalDate fromDate = currentMonth.atDay(1);
        LocalDate toDateExclusive = currentMonth.plusMonths(1).atDay(1);

        Instant from = fromDate.atStartOfDay(zoneId).toInstant();
        Instant to = toDateExclusive.atStartOfDay(zoneId).toInstant();

        Long ownerId = authenticatedUserProvider.requireCurrentUserId();
        var aggregate = transactionRepository.aggregateByOwnerAndPeriod(ownerId, from, to);
        return DashboardSummaryResponse.from(new PeriodResponse(fromDate, toDateExclusive), aggregate);
    }
}
