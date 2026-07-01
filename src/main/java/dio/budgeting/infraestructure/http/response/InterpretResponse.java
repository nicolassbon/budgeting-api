package dio.budgeting.infraestructure.http.response;

import dio.budgeting.domain.Category;
import dio.budgeting.infraestructure.ai.InterpretationResult;

public record InterpretResponse(String description, Long amount, Category category, String status) {
    public static InterpretResponse from(InterpretationResult result) {
        return new InterpretResponse(result.description(), result.amount(), result.category(), result.status().name());
    }
}
