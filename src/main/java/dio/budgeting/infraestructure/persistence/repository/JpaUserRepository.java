package dio.budgeting.infraestructure.persistence.repository;

import dio.budgeting.domain.user.User;
import dio.budgeting.domain.user.UserRepository;
import dio.budgeting.infraestructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JpaUserRepository implements UserRepository {
    private final UserEntityRepository userEntityRepository;

    public JpaUserRepository(UserEntityRepository userEntityRepository) {
        this.userEntityRepository = userEntityRepository;
    }

    @Override
    public Optional<User> findById(Long id) {
        return userEntityRepository.findById(id).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userEntityRepository.findByEmail(email).map(UserEntity::toDomain);
    }

    @Override
    public User save(User user) {
        return userEntityRepository.save(UserEntity.from(user)).toDomain();
    }
}
