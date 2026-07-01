package dio.budgeting.infraestructure.persistence.repository;

import dio.budgeting.domain.user.PasswordResetToken;
import dio.budgeting.domain.user.PasswordResetTokenRepository;
import dio.budgeting.infraestructure.persistence.entity.PasswordResetTokenEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class JpaPasswordResetTokenRepository implements PasswordResetTokenRepository {
    private final PasswordResetTokenEntityRepository tokenEntityRepository;

    public JpaPasswordResetTokenRepository(PasswordResetTokenEntityRepository tokenEntityRepository) {
        this.tokenEntityRepository = tokenEntityRepository;
    }

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return tokenEntityRepository.save(PasswordResetTokenEntity.from(token)).toDomain();
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return tokenEntityRepository.findByTokenHash(tokenHash).map(PasswordResetTokenEntity::toDomain);
    }

    @Override
    public void markUnusedTokensUsedForUser(Long userId, Instant usedAt) {
        tokenEntityRepository.markUnusedTokensUsedForUser(userId, usedAt);
    }
}
