package dio.budgeting.domain;

import lombok.Getter;

import java.time.Instant;

@Getter
public class Transaction {
    private TransactionId id;
    private String description;
    private long amountCents;
    private Category category;
    private Long ownerId;
    private Instant occurredAt;

    public Transaction(TransactionId id, String description, long amountCents, Category category) {
        this(id, description, amountCents, category, null, null);
    }

    public Transaction(TransactionId id, String description, long amountCents, Category category, Long ownerId) {
        this(id, description, amountCents, category, ownerId, null);
    }

    public Transaction(TransactionId id,
                       String description,
                       long amountCents,
                       Category category,
                       Long ownerId,
                       Instant occurredAt) {
        this.id = id;
        this.description = description;
        this.amountCents = amountCents;
        this.category = category;
        this.ownerId = ownerId;
        this.occurredAt = occurredAt;
    }

    public Transaction(String description, long amountCents, Category category) {
        this(new TransactionId(), description, amountCents, category, null, null);
    }

    public Transaction(String description, long amountCents, Category category, Long ownerId) {
        this(new TransactionId(), description, amountCents, category, ownerId, null);
    }

    public Transaction(String description, long amountCents, Category category, Long ownerId, Instant occurredAt) {
        this(new TransactionId(), description, amountCents, category, ownerId, occurredAt);
    }
}

