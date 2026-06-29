package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionId;
import dio.budgeting.domain.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionServiceTest {

    @Test
    void shouldCreateTransactionByDelegatingToRepositoryAndMappingOutput() {
        var repository = new FakeTransactionRepository();
        var service = new TransactionService(repository, () -> 42L);

        var output = service.create(new PersistTransactionInput("Supermarket", 1250L, Category.COMIDA));

        assertThat(repository.savedTransaction()).isNotNull();
        assertThat(repository.savedTransaction().getDescription()).isEqualTo("Supermarket");
        assertThat(repository.savedTransaction().getAmount()).isEqualTo(1250L);
        assertThat(repository.savedTransaction().getCategory()).isEqualTo(Category.COMIDA);
        assertThat(repository.savedTransaction().getOwnerId()).isEqualTo(42L);

        assertThat(output.id()).isEqualTo("99");
        assertThat(output.description()).isEqualTo("Supermarket");
        assertThat(output.category()).isEqualTo("COMIDA");
        assertThat(output.value()).isEqualTo(1250.0);
    }

    @Test
    void shouldListTransactionsByCategoryByDelegatingToRepositoryAndMappingOutput() {
        var repository = new FakeTransactionRepository();
        repository.transactionsToReturn = List.of(
                new Transaction(new TransactionId(10L), "Pharmacy", 450L, Category.FARMACIA)
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
                });
    }

    private static final class FakeTransactionRepository implements TransactionRepository {
        private Transaction savedTransaction;
        private Category requestedCategory;
        private Long requestedOwnerId;
        private List<Transaction> transactionsToReturn = List.of();

        @Override
        public Transaction save(Transaction transaction) {
            savedTransaction = transaction;
            return new Transaction(new TransactionId(99L), transaction.getDescription(), transaction.getAmount(), transaction.getCategory());
        }

        @Override
        public List<Transaction> findAllByCategoryAndOwnerId(Category category, Long ownerId) {
            requestedCategory = category;
            requestedOwnerId = ownerId;
            return transactionsToReturn;
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
    }
}
