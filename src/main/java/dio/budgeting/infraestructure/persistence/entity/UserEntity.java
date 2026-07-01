package dio.budgeting.infraestructure.persistence.entity;

import dio.budgeting.domain.user.User;
import dio.budgeting.domain.user.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "app_user")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "weekly_budget_amount", precision = 19, scale = 2)
    private BigDecimal weeklyBudgetAmount;

    public static UserEntity from(User user) {
        return new UserEntity(user.id(), user.email(), user.password(), user.role(), user.weeklyBudgetAmount());
    }

    public User toDomain() {
        return new User(id, email, password, role, weeklyBudgetAmount);
    }
}
