package dio.budgeting.application.auth;

import dio.budgeting.domain.user.User;
import dio.budgeting.domain.user.UserRepository;
import dio.budgeting.domain.user.UserRole;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest {

    @Test
    void shouldReadCurrentWeeklyBudgetWithNormalizedEmail() {
        var repository = new FakeUserRepository();
        repository.userToFind = Optional.of(userWithBudget(new BigDecimal("12500.50")));
        var service = new AuthService(repository, null, null);

        BigDecimal amount = service.currentWeeklyBudget(" User@Example.COM ");

        assertThat(repository.emailToFind).isEqualTo("user@example.com");
        assertThat(amount).isEqualByComparingTo("12500.50");
    }

    @Test
    void shouldReturnNullWhenCurrentWeeklyBudgetIsUnset() {
        var repository = new FakeUserRepository();
        repository.userToFind = Optional.of(userWithBudget(null));
        var service = new AuthService(repository, null, null);

        BigDecimal amount = service.currentWeeklyBudget("user@example.com");

        assertThat(amount).isNull();
    }

    @Test
    void shouldUpdateWeeklyBudgetAndSaveExistingUser() {
        var repository = new FakeUserRepository();
        repository.userToFind = Optional.of(userWithBudget(null));
        var service = new AuthService(repository, null, null);

        BigDecimal amount = service.updateWeeklyBudget("USER@example.com", new BigDecimal("900.75"));

        assertThat(repository.emailToFind).isEqualTo("user@example.com");
        assertThat(repository.savedUser.id()).isEqualTo(1L);
        assertThat(repository.savedUser.email()).isEqualTo("user@example.com");
        assertThat(repository.savedUser.password()).isEqualTo("encoded-password");
        assertThat(repository.savedUser.role()).isEqualTo(UserRole.USER);
        assertThat(repository.savedUser.weeklyBudgetAmount()).isEqualByComparingTo("900.75");
        assertThat(amount).isEqualByComparingTo("900.75");
    }

    @Test
    void shouldPersistNullWhenClearingWeeklyBudget() {
        var repository = new FakeUserRepository();
        repository.userToFind = Optional.of(userWithBudget(new BigDecimal("400.00")));
        var service = new AuthService(repository, null, null);

        BigDecimal amount = service.updateWeeklyBudget("user@example.com", null);

        assertThat(repository.savedUser.weeklyBudgetAmount()).isNull();
        assertThat(amount).isNull();
    }

    @Test
    void shouldFailWhenAuthenticatedUserCannotBeFound() {
        var repository = new FakeUserRepository();
        repository.userToFind = Optional.empty();
        var service = new AuthService(repository, null, null);

        assertThatThrownBy(() -> service.currentWeeklyBudget("missing@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Authenticated user not found");
    }

    private static User userWithBudget(BigDecimal amount) {
        return new User(1L, "user@example.com", "encoded-password", UserRole.USER, amount);
    }

    private static class FakeUserRepository implements UserRepository {
        private Optional<User> userToFind = Optional.empty();
        private String emailToFind;
        private User savedUser;

        @Override
        public Optional<User> findByEmail(String email) {
            emailToFind = email;
            return userToFind;
        }

        @Override
        public User save(User user) {
            savedUser = user;
            return user;
        }
    }
}
