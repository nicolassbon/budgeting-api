package dio.budgeting.application.auth;

import dio.budgeting.domain.user.User;

public record AuthenticatedUser(Long id, String email, String role) {
    static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.id(), user.email(), user.role().name());
    }
}
