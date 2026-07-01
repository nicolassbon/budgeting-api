package dio.budgeting.application.auth;

public record PasswordResetEmail(String to, String resetLink) {
}
