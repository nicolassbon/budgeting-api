package dio.budgeting.domain.user;

import java.math.BigDecimal;

public record User(Long id, String email, String password, UserRole role, BigDecimal weeklyBudgetAmount) {
}
