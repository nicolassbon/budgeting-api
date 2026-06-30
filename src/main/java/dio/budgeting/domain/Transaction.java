package dio.budgeting.domain;

import lombok.Getter;

import java.time.Instant;

@Getter
public class Transaction {
    private TransactionId id;
    private String description;
    private long amount;
    private Category category;
    private Long ownerId;
    private Instant occurredAt;

    public Transaction(TransactionId id, String description, long amount, Category category) {
        this(id, description, amount, category, null, null);
    }

    public Transaction(TransactionId id, String description, long amount, Category category, Long ownerId) {
        this(id, description, amount, category, ownerId, null);
    }

    public Transaction(TransactionId id,
                       String description,
                       long amount,
                       Category category,
                       Long ownerId,
                       Instant occurredAt) {
        this.id = id;
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.ownerId = ownerId;
        this.occurredAt = occurredAt;
    }

    public Transaction(String description, long amount, Category category) {
        this(new TransactionId(), description, amount, category, null, null);
    }

    public Transaction(String description, long amount, Category category, Long ownerId) {
        this(new TransactionId(), description, amount, category, ownerId, null);
    }

    public Transaction(String description, long amount, Category category, Long ownerId, Instant occurredAt) {
        this(new TransactionId(), description, amount, category, ownerId, occurredAt);
    }
}
