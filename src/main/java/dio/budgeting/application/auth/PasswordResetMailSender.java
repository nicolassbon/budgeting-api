package dio.budgeting.application.auth;

public interface PasswordResetMailSender {
    void send(PasswordResetEmail email);
}
