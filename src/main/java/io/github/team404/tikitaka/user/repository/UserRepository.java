package io.github.team404.tikitaka.user.repository;

import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, UserRepositoryCustom {

    // 이메일을 기준으로 사용자를 조회한다.
    Optional<User> findByEmail(String email);

    // 해당 이메일을 사용하는 사용자가 존재하는지 확인한다.
    boolean existsByEmail(String email);

    // OAuth 제공자와 해당 제공자의 사용자 고유 ID를 기준으로 사용자를 조회한다.
    Optional<User> findByProviderAndProviderId(
            OAuthProvider provider,
            String providerId
    );
}