package dio.budgeting.infraestructure.ai;

import dio.budgeting.domain.Category;

import java.util.Objects;

public record InterpretationResult(InterpretationStatus status, String description, Long amount, Category category) {
    public InterpretationResult {
        Objects.requireNonNull(status, "status must not be null");
    }

    public static InterpretationResult fromPayload(InterpretationPayload payload) {
        if (payload == null) {
            return new InterpretationResult(InterpretationStatus.INCOMPLETE, null, null, null);
        }

        if (payload.status() != null && payload.status().equalsIgnoreCase(InterpretationStatus.OUT_OF_SCOPE.name())) {
            return new InterpretationResult(InterpretationStatus.OUT_OF_SCOPE, null, null, null);
        }

        InterpretationStatus status = payload.description() != null && payload.amount() != null && payload.category() != null
                ? InterpretationStatus.OK
                : InterpretationStatus.INCOMPLETE;
        return new InterpretationResult(status, payload.description(), payload.amount(), payload.category());
    }
}
