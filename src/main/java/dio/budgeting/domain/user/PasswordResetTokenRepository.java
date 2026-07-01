package dio.budgeting.domain.user;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository {
    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void markUnusedTokensUsedForUser(Long userId, Instant usedAt);
}
