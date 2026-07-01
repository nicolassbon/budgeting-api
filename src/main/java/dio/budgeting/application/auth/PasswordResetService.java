package dio.budgeting.application.auth;

import dio.budgeting.domain.user.PasswordResetToken;
import dio.budgeting.domain.user.PasswordResetTokenRepository;
import dio.budgeting.domain.user.User;
import dio.budgeting.domain.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

@Service
public class PasswordResetService {
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetProperties properties;
    private final Clock clock;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordResetMailSender mailSender,
                                PasswordEncoder passwordEncoder,
                                PasswordResetProperties properties,
                                Clock clock) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = normalize(email);
        userRepository.findByEmail(normalizedEmail).ifPresent(this::createAndSendResetLink);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new PasswordResetTokenInvalidException();
        }
        String tokenHash = hash(rawToken);
        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByTokenHash(tokenHash)
                .filter(found -> found.isUsableAt(now))
                .orElseThrow(PasswordResetTokenInvalidException::new);
        User user = userRepository.findById(token.userId())
                .orElseThrow(PasswordResetTokenInvalidException::new);

        User updated = new User(user.id(), user.email(), passwordEncoder.encode(newPassword), user.role(), user.weeklyBudgetAmount());
        userRepository.save(updated);
        tokenRepository.save(token.markUsed(now));
    }

    private void createAndSendResetLink(User user) {
        Instant now = clock.instant();
        String rawToken = generateToken();
        tokenRepository.markUnusedTokensUsedForUser(user.id(), now);
        tokenRepository.save(new PasswordResetToken(
                null,
                user.id(),
                hash(rawToken),
                now.plus(properties.tokenTtl()),
                null,
                now
        ));
        mailSender.send(new PasswordResetEmail(user.email(), resetLink(rawToken)));
    }

    private String resetLink(String rawToken) {
        return UriComponentsBuilder.fromUriString(properties.resetBaseUrl())
                .queryParam("token", rawToken)
                .build()
                .toUriString();
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes());
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String normalize(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        return email.trim().toLowerCase();
    }
}
