package dio.budgeting.application.auth;

import dio.budgeting.domain.user.User;
import dio.budgeting.domain.user.UserRepository;
import dio.budgeting.domain.user.UserRole;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthenticatedUser register(String email, String password) {
        String normalizedEmail = normalize(email);
        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            throw new DuplicateEmailException(normalizedEmail);
        });

        User saved = userRepository.save(new User(null, normalizedEmail, passwordEncoder.encode(password), UserRole.USER));
        return AuthenticatedUser.from(saved);
    }

    public Authentication authenticate(String email, String password) {
        return authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(normalize(email), password));
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser currentUser(String email) {
        return userRepository.findByEmail(normalize(email))
                .map(AuthenticatedUser::from)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }

    private static String normalize(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        return email.trim().toLowerCase();
    }
}
