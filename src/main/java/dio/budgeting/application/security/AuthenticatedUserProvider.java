package dio.budgeting.application.security;

public interface AuthenticatedUserProvider {
    Long requireCurrentUserId();
}
