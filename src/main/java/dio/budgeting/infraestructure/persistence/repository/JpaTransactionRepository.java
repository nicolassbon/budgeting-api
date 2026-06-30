package dio.budgeting.infraestructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.DashboardAggregate;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionHistoryCriteria;
import dio.budgeting.domain.TransactionHistoryEntry;
import dio.budgeting.domain.TransactionRepository;
import dio.budgeting.infraestructure.persistence.entity.TransactionEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class JpaTransactionRepository implements TransactionRepository {
    private final TransactionEntityRepository transactionEntityRepository;

    public JpaTransactionRepository(TransactionEntityRepository transactionEntityRepository) {
        this.transactionEntityRepository = transactionEntityRepository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        TransactionEntity entity = TransactionEntity.from(transaction);
        return transactionEntityRepository.save(entity).toDomain();
    }

    @Override
    public List<Transaction> findAllByCategoryAndOwnerId(Category category, Long ownerId) {
        return transactionEntityRepository.findAllByCategoryAndOwnerId(category, ownerId)
                .stream()
                .map(TransactionEntity::toDomain)
                .toList();
    }

    @Override
    public List<TransactionHistoryEntry> findHistory(TransactionHistoryCriteria criteria) {
        return transactionEntityRepository.findHistory(criteria)
                .stream()
                .map(TransactionEntity::toHistoryEntry)
                .toList();
    }

    @Override
    public DashboardAggregate aggregateByOwnerAndPeriod(Long ownerId, Instant from, Instant to) {
        return transactionEntityRepository.aggregateByOwnerAndPeriod(ownerId, from, to);
    }
}
