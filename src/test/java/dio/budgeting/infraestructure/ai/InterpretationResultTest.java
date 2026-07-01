package dio.budgeting.infraestructure.ai;

import dio.budgeting.domain.Category;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterpretationResultTest {

    @Test
    void shouldExposeStatusAndDraftFields() {
        InterpretationResult result = new InterpretationResult(InterpretationStatus.OK, "Coffee", 2300L, Category.COMIDA);

        assertThat(result.status()).isEqualTo(InterpretationStatus.OK);
        assertThat(result.description()).isEqualTo("Coffee");
        assertThat(result.amount()).isEqualTo(2300L);
        assertThat(result.category()).isEqualTo(Category.COMIDA);
        assertThat(InterpretationStatus.values()).containsExactly(InterpretationStatus.OK, InterpretationStatus.INCOMPLETE, InterpretationStatus.OUT_OF_SCOPE);
    }

    @Test
    void shouldAllowNullDraftFieldsForIncompleteResult() {
        InterpretationResult result = new InterpretationResult(InterpretationStatus.INCOMPLETE, null, null, null);

        assertThat(result.status()).isEqualTo(InterpretationStatus.INCOMPLETE);
        assertThat(result.description()).isNull();
        assertThat(result.amount()).isNull();
        assertThat(result.category()).isNull();
    }

    @Test
    void shouldRejectNullStatus() {
        assertThatThrownBy(() -> new InterpretationResult(null, "Coffee", 2300L, Category.COMIDA))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("status must not be null");
    }
}
