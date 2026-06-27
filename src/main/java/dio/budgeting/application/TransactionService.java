package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.domain.Category;
import dio.budgeting.domain.Transaction;
import dio.budgeting.domain.TransactionRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Tool(name = "persist-transaction", description = "Persiste una nueva transacción financiera")
    public TransactionOutput create(PersistTransactionInput input) {
        var transaction = transactionRepository.save(new Transaction(input.description(), input.amount(), input.category()));
        return TransactionOutput.from(transaction);
    }

    @Tool(name = "list-transactions-by-category", description = "Lista transacciones financieras por categoría")
    public List<TransactionOutput> findAllByCategory(@ToolParam(description = "Categoría de una transacción") Category category) {
        return transactionRepository.findAllByCategory(category)
                .stream()
                .map(TransactionOutput::from)
                .toList();
    }
}
