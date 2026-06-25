package dio.budgeting.infraestructure.persistence.entity;

import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    public static TransactionEntity from(Transaction transaction) {
        return new TransactionEntity(
                transaction.getId().id(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getCategory()
        );
    }

    public Transaction toDomain() {
        return new Transaction(
                new TransactionId(this.id),
                this.description,
                this.amount,
                this.category
        );
    }
}
