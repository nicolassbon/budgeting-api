package dio.budgeting.application;

import dio.budgeting.application.input.PersistTransactionInput;
import dio.budgeting.application.output.TransactionOutput;
import dio.budgeting.application.security.AuthenticatedUserProvider;
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
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public TransactionService(TransactionRepository transactionRepository,
                              AuthenticatedUserProvider authenticatedUserProvider) {
        this.transactionRepository = transactionRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @Tool(name = "persist-transaction", description = "Persiste una nueva transacción financiera")
    public TransactionOutput create(PersistTransactionInput input) {
        Long ownerId = authenticatedUserProvider.requireCurrentUserId();
        var transaction = transactionRepository.save(new Transaction(input.description(), input.amount(), input.category(), ownerId));
        return TransactionOutput.from(transaction);
    }

    @Tool(name = "list-transactions-by-category", description = "Lista transacciones financieras por categoría")
    public List<TransactionOutput> findAllByCategory(@ToolParam(description = "Categoría de una transacción") Category category) {
        Long ownerId = authenticatedUserProvider.requireCurrentUserId();
        return transactionRepository.findAllByCategoryAndOwnerId(category, ownerId)
                .stream()
                .map(TransactionOutput::from)
                .toList();
    }
}
