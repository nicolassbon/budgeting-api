package dio.budgeting.infraestructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.DashboardAggregate;
import dio.budgeting.domain.TransactionHistoryCriteria;
import dio.budgeting.infraestructure.persistence.entity.TransactionEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TransactionEntityRepository extends CrudRepository<TransactionEntity, Long> {

    Optional<TransactionEntity> findByIdAndOwnerId(Long id, Long ownerId);

    List<TransactionEntity> findAllByCategoryAndOwnerId(Category category, Long ownerId);

    List<TransactionEntity> findAllByOwnerIdOrderByOccurredAtDesc(Long ownerId);

    List<TransactionEntity> findAllByOwnerIdAndCategoryOrderByOccurredAtDesc(Long ownerId,
                                                                              Category category);

    @Query("""
            SELECT t
            FROM TransactionEntity t
            WHERE t.ownerId = :ownerId
              AND t.occurredAt >= :from
            ORDER BY t.occurredAt DESC
            """)
    List<TransactionEntity> findHistoryFrom(@Param("ownerId") Long ownerId,
                                            @Param("from") Instant from);

    @Query("""
            SELECT t
            FROM TransactionEntity t
            WHERE t.ownerId = :ownerId
              AND t.category = :category
              AND t.occurredAt >= :from
            ORDER BY t.occurredAt DESC
            """)
    List<TransactionEntity> findHistoryFromByCategory(@Param("ownerId") Long ownerId,
                                                      @Param("category") Category category,
                                                      @Param("from") Instant from);

    @Query("""
            SELECT t
            FROM TransactionEntity t
            WHERE t.ownerId = :ownerId
              AND t.occurredAt < :to
            ORDER BY t.occurredAt DESC
            """)
    List<TransactionEntity> findHistoryTo(@Param("ownerId") Long ownerId,
                                          @Param("to") Instant to);

    @Query("""
            SELECT t
            FROM TransactionEntity t
            WHERE t.ownerId = :ownerId
              AND t.category = :category
              AND t.occurredAt < :to
            ORDER BY t.occurredAt DESC
            """)
    List<TransactionEntity> findHistoryToByCategory(@Param("ownerId") Long ownerId,
                                                    @Param("category") Category category,
                                                    @Param("to") Instant to);

    @Query("""
            SELECT t
            FROM TransactionEntity t
            WHERE t.ownerId = :ownerId
              AND t.occurredAt >= :from
              AND t.occurredAt < :to
            ORDER BY t.occurredAt DESC
            """)
    List<TransactionEntity> findHistoryInRange(@Param("ownerId") Long ownerId,
                                               @Param("from") Instant from,
                                               @Param("to") Instant to);

    @Query("""
            SELECT t
            FROM TransactionEntity t
            WHERE t.ownerId = :ownerId
              AND t.category = :category
              AND t.occurredAt >= :from
              AND t.occurredAt < :to
            ORDER BY t.occurredAt DESC
            """)
    List<TransactionEntity> findHistoryInRangeByCategory(@Param("ownerId") Long ownerId,
                                                         @Param("category") Category category,
                                                         @Param("from") Instant from,
                                                         @Param("to") Instant to);

    @Query("""
            SELECT COALESCE(SUM(t.amountCents), 0) AS totalAmount, COUNT(t) AS transactionCount
            FROM TransactionEntity t
            WHERE t.ownerId = :ownerId
              AND t.occurredAt >= :from
              AND t.occurredAt < :to
            """)
    SummaryProjection summarizeByOwnerAndPeriod(@Param("ownerId") Long ownerId,
                                                @Param("from") Instant from,
                                                @Param("to") Instant to);

    @Query("""
            SELECT t.category AS category, COALESCE(SUM(t.amountCents), 0) AS totalAmount, COUNT(t) AS transactionCount
            FROM TransactionEntity t
            WHERE t.ownerId = :ownerId
              AND t.occurredAt >= :from
              AND t.occurredAt < :to
            GROUP BY t.category
            ORDER BY SUM(t.amountCents) DESC
            """)
    List<CategoryAggregateProjection> aggregateCategoriesByOwnerAndPeriod(@Param("ownerId") Long ownerId,
                                                                          @Param("from") Instant from,
                                                                          @Param("to") Instant to);

    default List<TransactionEntity> findHistory(TransactionHistoryCriteria criteria) {
        boolean hasFrom = criteria.from().isPresent();
        boolean hasTo = criteria.to().isPresent();
        boolean hasCategory = criteria.category().isPresent();

        if (hasFrom && hasTo) {
            if (hasCategory) {
                return findHistoryInRangeByCategory(
                        criteria.ownerId(),
                        criteria.category().orElseThrow(),
                        criteria.from().orElseThrow(),
                        criteria.to().orElseThrow()
                );
            }
            return findHistoryInRange(
                    criteria.ownerId(),
                    criteria.from().orElseThrow(),
                    criteria.to().orElseThrow()
            );
        }

        if (hasFrom) {
            if (hasCategory) {
                return findHistoryFromByCategory(
                        criteria.ownerId(),
                        criteria.category().orElseThrow(),
                        criteria.from().orElseThrow()
                );
            }
            return findHistoryFrom(criteria.ownerId(), criteria.from().orElseThrow());
        }

        if (hasTo) {
            if (hasCategory) {
                return findHistoryToByCategory(
                        criteria.ownerId(),
                        criteria.category().orElseThrow(),
                        criteria.to().orElseThrow()
                );
            }
            return findHistoryTo(criteria.ownerId(), criteria.to().orElseThrow());
        }

        if (hasCategory) {
            return findAllByOwnerIdAndCategoryOrderByOccurredAtDesc(
                    criteria.ownerId(),
                    criteria.category().orElseThrow()
            );
        }

        return findAllByOwnerIdOrderByOccurredAtDesc(criteria.ownerId());
    }

    default DashboardAggregate aggregateByOwnerAndPeriod(Long ownerId, Instant from, Instant to) {
        var summary = summarizeByOwnerAndPeriod(ownerId, from, to);
        var aggregates = aggregateCategoriesByOwnerAndPeriod(ownerId, from, to).stream()
                .map(row -> new DashboardAggregate.CategoryAggregate(
                        row.getCategory(),
                        row.getTotalAmount(),
                        row.getTransactionCount()
                ))
                .toList();
        return new DashboardAggregate(summary.getTotalAmount(), summary.getTransactionCount(), aggregates);
    }

    interface SummaryProjection {
        long getTotalAmount();
        long getTransactionCount();
    }

    interface CategoryAggregateProjection {
        Category getCategory();
        long getTotalAmount();
        long getTransactionCount();
    }
}
