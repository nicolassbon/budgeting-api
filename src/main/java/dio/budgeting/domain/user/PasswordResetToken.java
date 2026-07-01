package dio.budgeting.domain.user;

import java.time.Instant;

public record PasswordResetToken(
        Long id,
        Long userId,
        String tokenHash,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt
) {
    public boolean isUsableAt(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public PasswordResetToken markUsed(Instant usedAt) {
        return new PasswordResetToken(id, userId, tokenHash, expiresAt, usedAt, createdAt);
    }
}
