package dio.budgeting.infraestructure.security;

import dio.budgeting.application.security.AuthenticatedUserProvider;
import dio.budgeting.domain.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextAuthenticatedUserProvider implements AuthenticatedUserProvider {
    private final UserRepository userRepository;

    public SecurityContextAuthenticatedUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Long requireCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }

        return userRepository.findByEmail(authentication.getName())
                .map(user -> user.id())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is not available"));
    }
}
