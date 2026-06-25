package dio.budgeting.domain;

public record TransactionId(Long id) {
    public TransactionId() {
        this(null);
    }
}
