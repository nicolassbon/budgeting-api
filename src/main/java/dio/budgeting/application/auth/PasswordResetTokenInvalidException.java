package dio.budgeting.application.auth;

public class PasswordResetTokenInvalidException extends RuntimeException {
    public PasswordResetTokenInvalidException() {
        super("Password reset token is invalid or expired");
    }
}
