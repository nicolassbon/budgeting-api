package dio.budgeting.application;

import dio.budgeting.application.output.DashboardSummaryResponse;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.DashboardAggregate;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionHistoryCriteria;
import dio.budgeting.domain.TransactionHistoryEntry;
import dio.budgeting.domain.TransactionId;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DashboardServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-29T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldBuildDashboardSummaryForCurrentMonthScopedToAuthenticatedOwner() {
        var repository = new FakeRepository();
        repository.aggregate = new DashboardAggregate(
                5750L,
                3L,
                List.of(
                        new DashboardAggregate.CategoryAggregate(Category.COMIDA, 3500L, 2L),
                        new DashboardAggregate.CategoryAggregate(Category.TRANSPORTE, 2250L, 1L)
                )
        );
        var service = new DashboardService(repository, () -> 99L, FIXED_CLOCK);

        DashboardSummaryResponse summary = service.currentMonthSummary();

        assertThat(repository.requestedOwnerId).isEqualTo(99L);
        assertThat(summary.transactionCount()).isEqualTo(3L);
        assertThat(summary.totalAmountCents()).isEqualTo(5750L);
        assertThat(summary.totalAmount()).isEqualTo(57.5);
        assertThat(summary.period().from()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(summary.period().to()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(summary.topCategories())
                .extracting("category", "totalAmount", "transactionCount")
                .containsExactly(
                        tuple("COMIDA", 35.0, 2L),
                        tuple("TRANSPORTE", 22.5, 1L)
                );
    }

    @Test
    void shouldReturnEmptySummaryWhenOwnerHasNoTransactionsInCurrentPeriod() {
        var repository = new FakeRepository();
        repository.aggregate = new DashboardAggregate(0L, 0L, List.of());
        var service = new DashboardService(repository, () -> 7L, FIXED_CLOCK);

        DashboardSummaryResponse summary = service.currentMonthSummary();

        assertThat(repository.requestedOwnerId).isEqualTo(7L);
        assertThat(summary.transactionCount()).isEqualTo(0L);
        assertThat(summary.totalAmountCents()).isEqualTo(0L);
        assertThat(summary.totalAmount()).isEqualTo(0.0);
        assertThat(summary.topCategories()).isEmpty();
        assertThat(summary.period().from()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(summary.period().to()).isEqualTo(LocalDate.parse("2026-07-01"));
    }

    @Test
    void shouldRequestAggregateWithUtcStartAndExclusiveNextMonthStart() {
        var repository = new FakeRepository();
        repository.aggregate = new DashboardAggregate(0L, 0L, List.of());
        var service = new DashboardService(repository, () -> 1L, FIXED_CLOCK);

        service.currentMonthSummary();

        assertThat(repository.requestedFrom).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(repository.requestedTo).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
    }

    private static final class FakeRepository implements TransactionRepository {

        private DashboardAggregate aggregate;
        private Long requestedOwnerId;
        private Instant requestedFrom;
        private Instant requestedTo;

        @Override
        public Transaction save(Transaction transaction) {
            return transaction;
        }

        @Override
        public Optional<Transaction> findByIdAndOwnerId(TransactionId id, Long ownerId) {
            return Optional.empty();
        }

        @Override
        public List<Transaction> findAllByCategoryAndOwnerId(Category category, Long ownerId) {
            return List.of();
        }

        @Override
        public List<TransactionHistoryEntry> findHistory(TransactionHistoryCriteria criteria) {
            return List.of();
        }

        @Override
        public DashboardAggregate aggregateByOwnerAndPeriod(Long ownerId, Instant from, Instant to) {
            this.requestedOwnerId = ownerId;
            this.requestedFrom = from;
            this.requestedTo = to;
            return aggregate;
        }
    }
}
