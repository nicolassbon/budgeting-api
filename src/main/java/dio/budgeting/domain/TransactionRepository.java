package dio.budgeting.domain;

import java.time.Instant;
import java.util.List;

public interface TransactionRepository {
    Transaction save(Transaction transaction);
    List<Transaction> findAllByCategoryAndOwnerId(Category category, Long ownerId);
    List<TransactionHistoryEntry> findHistory(TransactionHistoryCriteria criteria);
    DashboardAggregate aggregateByOwnerAndPeriod(Long ownerId, Instant from, Instant to);
}
