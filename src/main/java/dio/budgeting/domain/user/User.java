package dio.budgeting.domain.user;

public record User(Long id, String email, String password, UserRole role) {
}
