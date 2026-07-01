package dio.budgeting.infraestructure.persistence.entity;

import dio.budgeting.domain.user.PasswordResetToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "password_reset_token")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PasswordResetTokenEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PasswordResetTokenEntity from(PasswordResetToken token) {
        return new PasswordResetTokenEntity(
                token.id(),
                token.userId(),
                token.tokenHash(),
                token.expiresAt(),
                token.usedAt(),
                token.createdAt()
        );
    }

    public PasswordResetToken toDomain() {
        return new PasswordResetToken(id, userId, tokenHash, expiresAt, usedAt, createdAt);
    }
}
