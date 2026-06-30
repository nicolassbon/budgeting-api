package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.input.TransactionHistoryFilters;
import dio.budgeting.application.output.TransactionHistoryResponse;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.DashboardAggregate;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionHistoryCriteria;
import dio.budgeting.domain.TransactionHistoryEntry;
import dio.budgeting.domain.TransactionId;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TransactionServiceTest {

    @Test
    void shouldCreateTransactionByDelegatingToRepositoryAndMappingOutput() {
        var repository = new FakeTransactionRepository();
        var service = new TransactionService(repository, () -> 42L);

        var output = service.create(new PersistTransactionInput("Supermarket", 1250L, Category.COMIDA, null));

        assertThat(repository.savedTransaction()).isNotNull();
        assertThat(repository.savedTransaction().getDescription()).isEqualTo("Supermarket");
        assertThat(repository.savedTransaction().getAmount()).isEqualTo(1250L);
        assertThat(repository.savedTransaction().getCategory()).isEqualTo(Category.COMIDA);
        assertThat(repository.savedTransaction().getOwnerId()).isEqualTo(42L);
        assertThat(repository.savedTransaction().getOccurredAt())
                .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));

        assertThat(output.id()).isEqualTo("99");
        assertThat(output.description()).isEqualTo("Supermarket");
        assertThat(output.category()).isEqualTo("COMIDA");
        assertThat(output.value()).isEqualTo(1250.0);
    }

    @Test
    void shouldCreateTransactionWithExplicitOccurredAt() {
        var repository = new FakeTransactionRepository();
        var service = new TransactionService(repository, () -> 42L);
        Instant explicit = Instant.parse("2026-03-15T10:00:00Z");

        var output = service.create(new PersistTransactionInput("Lunch", 1000L, Category.COMIDA, explicit));

        assertThat(repository.savedTransaction().getOccurredAt()).isEqualTo(explicit);
        assertThat(output.date()).isEqualTo(explicit);
    }

    @Test
    void shouldListTransactionsByCategoryByDelegatingToRepositoryAndMappingOutput() {
        var repository = new FakeTransactionRepository();
        repository.transactionsToReturn = List.of(
                new Transaction(new TransactionId(10L), "Pharmacy", 450L, Category.FARMACIA, 84L, Instant.parse("2026-03-01T00:00:00Z"))
        );
        var service = new TransactionService(repository, () -> 84L);

        var outputs = service.findAllByCategory(Category.FARMACIA);

        assertThat(repository.requestedCategory()).isEqualTo(Category.FARMACIA);
        assertThat(repository.requestedOwnerId()).isEqualTo(84L);
        assertThat(outputs)
                .singleElement()
                .satisfies(output -> {
                    assertThat(output.id()).isEqualTo("10");
                    assertThat(output.description()).isEqualTo("Pharmacy");
                    assertThat(output.category()).isEqualTo("FARMACIA");
                    assertThat(output.value()).isEqualTo(450.0);
                    assertThat(output.date()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
                });
    }

    @Test
    void shouldFindHistoryScopedToAuthenticatedOwnerWithinDateRange() {
        var repository = new FakeTransactionRepository();
        repository.historyToReturn = List.of(
                new TransactionHistoryEntry(new TransactionId(1L), "Coffee", 500L, Category.COMIDA, Instant.parse("2026-03-10T08:00:00Z"))
        );
        var service = new TransactionService(repository, () -> 7L);
        var filters = new TransactionHistoryFilters(
                Optional.of(Instant.parse("2026-03-01T00:00:00Z")),
                Optional.of(Instant.parse("2026-04-01T00:00:00Z")),
                Optional.empty()
        );

        var response = service.findHistory(filters);

        assertThat(repository.lastHistoryFilters()).isNotNull();
        assertThat(repository.lastHistoryFilters().ownerId()).isEqualTo(7L);
        assertThat(repository.lastHistoryFilters().from()).contains(Instant.parse("2026-03-01T00:00:00Z"));
        assertThat(repository.lastHistoryFilters().to()).contains(Instant.parse("2026-04-01T00:00:00Z"));
        assertThat(repository.lastHistoryFilters().category()).isEmpty();
        assertThat(response.items())
                .singleElement()
                .satisfies(output -> {
                    assertThat(output.id()).isEqualTo("1");
                    assertThat(output.description()).isEqualTo("Coffee");
                    assertThat(output.category()).isEqualTo("COMIDA");
                    assertThat(output.value()).isEqualTo(500.0);
                    assertThat(output.date()).isEqualTo(Instant.parse("2026-03-10T08:00:00Z"));
                });
        assertThat(response.totalAmountCents()).isEqualTo(500L);
        assertThat(response.totalAmount()).isEqualTo(500.0);
        assertThat(response.transactionCount()).isEqualTo(1L);
    }

    @Test
    void shouldFindHistoryWithCategoryFilter() {
        var repository = new FakeTransactionRepository();
        repository.historyToReturn = List.of();
        var service = new TransactionService(repository, () -> 7L);
        var filters = new TransactionHistoryFilters(
                Optional.of(Instant.parse("2026-03-01T00:00:00Z")),
                Optional.of(Instant.parse("2026-04-01T00:00:00Z")),
                Optional.of(Category.FARMACIA)
        );

        var response = service.findHistory(filters);

        assertThat(repository.lastHistoryFilters().category()).contains(Category.FARMACIA);
        assertThat(response.items()).isEmpty();
        assertThat(response.totalAmountCents()).isEqualTo(0L);
        assertThat(response.totalAmount()).isEqualTo(0.0);
        assertThat(response.transactionCount()).isEqualTo(0L);
    }

    @Test
    void shouldComputeHistoryTotalsFromAllRepositoryEntries() {
        var repository = new FakeTransactionRepository();
        repository.historyToReturn = List.of(
                new TransactionHistoryEntry(new TransactionId(1L), "Coffee", 500L, Category.COMIDA, Instant.parse("2026-03-10T08:00:00Z")),
                new TransactionHistoryEntry(new TransactionId(2L), "Subway", 2250L, Category.TRANSPORTE, Instant.parse("2026-03-12T08:00:00Z")),
                new TransactionHistoryEntry(new TransactionId(3L), "Groceries", 3000L, Category.COMIDA, Instant.parse("2026-03-15T08:00:00Z"))
        );
        var service = new TransactionService(repository, () -> 7L);
        var filters = new TransactionHistoryFilters(
                Optional.of(Instant.parse("2026-03-01T00:00:00Z")),
                Optional.of(Instant.parse("2026-04-01T00:00:00Z")),
                Optional.empty()
        );

        var response = service.findHistory(filters);

        assertThat(response.transactionCount()).isEqualTo(3L);
        assertThat(response.totalAmountCents()).isEqualTo(5750L);
        assertThat(response.totalAmount()).isEqualTo(5750.0);
        assertThat(response.items()).hasSize(3);
    }

    private static final class FakeTransactionRepository implements TransactionRepository {
        private Transaction savedTransaction;
        private Category requestedCategory;
        private Long requestedOwnerId;
        private List<Transaction> transactionsToReturn = List.of();
        private List<TransactionHistoryEntry> historyToReturn = List.of();
        private TransactionHistoryCriteria lastHistoryFilters;

        @Override
        public Transaction save(Transaction transaction) {
            savedTransaction = transaction;
            return new Transaction(
                    new TransactionId(99L),
                    transaction.getDescription(),
                    transaction.getAmount(),
                    transaction.getCategory(),
                    transaction.getOwnerId(),
                    transaction.getOccurredAt()
            );
        }

        @Override
        public List<Transaction> findAllByCategoryAndOwnerId(Category category, Long ownerId) {
            requestedCategory = category;
            requestedOwnerId = ownerId;
            return transactionsToReturn;
        }

        @Override
        public List<TransactionHistoryEntry> findHistory(TransactionHistoryCriteria criteria) {
            this.lastHistoryFilters = criteria;
            return historyToReturn;
        }

        @Override
        public DashboardAggregate aggregateByOwnerAndPeriod(Long ownerId, Instant from, Instant to) {
            return new DashboardAggregate(0L, 0L, List.of());
        }

        private Transaction savedTransaction() {
            return savedTransaction;
        }

        private Category requestedCategory() {
            return requestedCategory;
        }

        private Long requestedOwnerId() {
            return requestedOwnerId;
        }

        private TransactionHistoryCriteria lastHistoryFilters() {
            return lastHistoryFilters;
        }
    }
}
