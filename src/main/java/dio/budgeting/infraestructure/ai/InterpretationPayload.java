package dio.budgeting.infraestructure.ai;

import dio.budgeting.domain.Category;

record InterpretationPayload(String status, String description, Long amount, Category category) {
}
