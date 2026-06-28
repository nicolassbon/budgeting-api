package dio.budgeting.infraestructure.persistence.repository;

import dio.budgeting.domain.Category;
import dio.budgeting.infraestructure.persistence.entity.TransactionEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TransactionEntityRepository extends CrudRepository<TransactionEntity, Long> {

    List<TransactionEntity> findAllByCategoryAndOwnerId(Category category, Long ownerId);
}
