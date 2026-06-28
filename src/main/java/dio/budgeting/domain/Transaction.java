package dio.budgeting.domain;

import lombok.Getter;

@Getter
public class Transaction {
    private TransactionId id;
    private String description;
    private long amount;
    private Category category;
    private Long ownerId;

    public Transaction(TransactionId id, String description, long amount, Category category) {
        this(id, description, amount, category, null);
    }

    public Transaction(TransactionId id, String description, long amount, Category category, Long ownerId) {
        this.id = id;
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.ownerId = ownerId;
    }

    public Transaction(String description, long amount, Category category) {
        this.id = new TransactionId();
        this.description = description;
        this.amount = amount;
        this.category = category;
    }

    public Transaction(String description, long amount, Category category, Long ownerId) {
        this.id = new TransactionId();
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.ownerId = ownerId;
    }
}
