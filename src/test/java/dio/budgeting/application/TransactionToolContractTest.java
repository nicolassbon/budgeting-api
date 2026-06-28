package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.output.TransactionOutput;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionToolContractTest {

    @Test
    void shouldKeepTransactionToolMetadataStable() throws NoSuchMethodException {
        var createTool = TransactionService.class.getMethod("create", PersistTransactionInput.class).getAnnotation(Tool.class);
        var listTool = TransactionService.class.getMethod("findAllByCategory", dio.budgeting.domain.Category.class).getAnnotation(Tool.class);

        assertThat(createTool).isNotNull();
        assertThat(createTool.name()).isEqualTo("persist-transaction");
        assertThat(createTool.description()).isEqualTo("Persiste una nueva transacción financiera");

        assertThat(listTool).isNotNull();
        assertThat(listTool.name()).isEqualTo("list-transactions-by-category");
        assertThat(listTool.description()).isEqualTo("Lista transacciones financieras por categoría");
    }

    @Test
    void shouldKeepPersistTransactionInputToolParametersStable() throws NoSuchMethodException {
        var constructor = PersistTransactionInput.class.getDeclaredConstructor(String.class, long.class, dio.budgeting.domain.Category.class);
        var paramsByName = Arrays.stream(constructor.getParameters())
                .collect(Collectors.toMap(Parameter::getName, Function.identity()));

        assertToolParam(paramsByName, "description", "Descripción del gasto");
        assertToolParam(paramsByName, "amount", "Valor del gasto (en centavos)");
        assertToolParam(paramsByName, "category", "Categoria de una transacción");

        Method factory = PersistTransactionInput.class.getDeclaredMethod("of", String.class, long.class, dio.budgeting.domain.Category.class);
        assertThat(factory).isNotNull();
    }

    @Test
    void shouldKeepTransactionOutputRecordFieldsStable() {
        assertThat(Arrays.stream(TransactionOutput.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList())
                .containsExactly("id", "description", "category", "value");

        assertThat(Arrays.stream(dio.budgeting.infraestructure.http.response.TransactionResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList())
                .containsExactly("id", "description", "category", "amount");
    }

    private static void assertToolParam(Map<String, Parameter> paramsByName, String fieldName, String description) {
        var parameter = paramsByName.get(fieldName);
        var toolParam = parameter == null ? null : parameter.getAnnotation(ToolParam.class);

        assertThat(parameter).isNotNull();
        assertThat(toolParam).isNotNull();
        assertThat(toolParam.description()).isEqualTo(description);
    }
}
