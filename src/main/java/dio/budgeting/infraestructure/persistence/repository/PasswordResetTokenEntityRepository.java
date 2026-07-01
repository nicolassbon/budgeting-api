package dio.budgeting.infraestructure.persistence.repository;

import dio.budgeting.infraestructure.persistence.entity.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenEntityRepository extends CrudRepository<PasswordResetTokenEntity, Long> {
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("""
            update PasswordResetTokenEntity token
            set token.usedAt = :usedAt
            where token.userId = :userId and token.usedAt is null
            """)
    void markUnusedTokensUsedForUser(Long userId, Instant usedAt);
}
