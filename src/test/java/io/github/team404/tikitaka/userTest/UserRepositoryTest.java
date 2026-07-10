package io.github.team404.tikitaka.userTest;

import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void 이메일로_유저를_조회할_수_있다() {
        // given
        User user = new User(
                "test@gmail.com",
                "테스트유저",
                OAuthProvider.GOOGLE,
                "google-user-123"
        );

        userRepository.saveAndFlush(user);

        // when
        Optional<User> foundUser =
                userRepository.findByEmail("test@gmail.com");

        // then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail())
                .isEqualTo("test@gmail.com");
        assertThat(foundUser.get().getName())
                .isEqualTo("테스트유저");
        assertThat(foundUser.get().getProvider())
                .isEqualTo(OAuthProvider.GOOGLE);
        assertThat(foundUser.get().getProviderId())
                .isEqualTo("google-user-123");
    }

    @Test
    void 존재하지_않는_이메일은_조회되지_않는다() {
        // when
        Optional<User> foundUser =
                userRepository.findByEmail("unknown@gmail.com");

        // then
        assertThat(foundUser).isEmpty();
    }

    @Test
    void 이메일이_존재하면_true를_반환한다() {
        // given
        User user = new User(
                "test@gmail.com",
                "테스트유저",
                OAuthProvider.GOOGLE,
                "google-user-123"
        );

        userRepository.saveAndFlush(user);

        // when
        boolean exists =
                userRepository.existsByEmail("test@gmail.com");

        // then
        assertThat(exists).isTrue();
    }

    @Test
    void 이메일이_존재하지_않으면_false를_반환한다() {
        // when
        boolean exists =
                userRepository.existsByEmail("unknown@gmail.com");

        // then
        assertThat(exists).isFalse();
    }

    @Test
    void 동일한_이메일을_중복_저장할_수_없다() {
        // given
        User firstUser = new User(
                "test@gmail.com",
                "유저1",
                OAuthProvider.GOOGLE,
                "google-user-123"
        );

        User secondUser = new User(
                "test@gmail.com",
                "유저2",
                OAuthProvider.GOOGLE,
                "google-user-456"
        );

        userRepository.saveAndFlush(firstUser);

        // when & then
        assertThatThrownBy(() ->
                userRepository.saveAndFlush(secondUser)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void provider와_providerId로_유저를_조회할_수_있다() {
        // given
        User user = new User(
                "test@gmail.com",
                "테스트유저",
                OAuthProvider.GOOGLE,
                "google-user-123"
        );

        userRepository.saveAndFlush(user);

        // when
        Optional<User> foundUser =
                userRepository.findByProviderAndProviderId(
                        OAuthProvider.GOOGLE,
                        "google-user-123"
                );

        // then
        assertThat(foundUser).isPresent();
        assertThat(foundUser.get().getEmail())
                .isEqualTo("test@gmail.com");
        assertThat(foundUser.get().getProvider())
                .isEqualTo(OAuthProvider.GOOGLE);
        assertThat(foundUser.get().getProviderId())
                .isEqualTo("google-user-123");
    }

    @Test
    void 존재하지_않는_providerId는_조회되지_않는다() {
        // when
        Optional<User> foundUser =
                userRepository.findByProviderAndProviderId(
                        OAuthProvider.GOOGLE,
                        "unknown-provider-id"
                );

        // then
        assertThat(foundUser).isEmpty();
    }
}