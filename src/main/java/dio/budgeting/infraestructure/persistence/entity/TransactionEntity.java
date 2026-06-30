package dio.budgeting.infraestructure.persistence.entity;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionHistoryEntry;
import dio.budgeting.domain.TransactionId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String description;
    private long amount;

    @Enumerated(EnumType.STRING)
    private Category category;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static TransactionEntity from(Transaction transaction) {
        return new TransactionEntity(
                transaction.getId().id(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getCategory(),
                transaction.getOwnerId(),
                transaction.getOccurredAt()
        );
    }

    public Transaction toDomain() {
        return new Transaction(
                new TransactionId(this.id),
                this.description,
                this.amount,
                this.category,
                this.ownerId,
                this.occurredAt
        );
    }

    public TransactionHistoryEntry toHistoryEntry() {
        return new TransactionHistoryEntry(
                new TransactionId(this.id),
                this.description,
                this.amount,
                this.category,
                this.occurredAt
        );
    }
}
