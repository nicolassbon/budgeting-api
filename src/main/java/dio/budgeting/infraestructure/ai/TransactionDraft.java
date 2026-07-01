package dio.budgeting.infraestructure.ai;

import dio.budgeting.domain.Category;

public record TransactionDraft(String description, Long amount, Category category) {
}
