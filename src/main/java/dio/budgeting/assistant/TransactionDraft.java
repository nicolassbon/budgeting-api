package dio.budgeting.assistant;

import dio.budgeting.domain.Category;

public record TransactionDraft(String description, Long amount, Category category) {
}
