package dio.budgeting.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    Transaction save(Transaction transaction);
    Optional<Transaction> findByIdAndOwnerId(TransactionId id, Long ownerId);
    List<Transaction> findAllByCategoryAndOwnerId(Category category, Long ownerId);
    List<TransactionHistoryEntry> findHistory(TransactionHistoryCriteria criteria);
    DashboardAggregate aggregateByOwnerAndPeriod(Long ownerId, Instant from, Instant to);
}
