package io.github.team404.tikitaka.userTest.service;


import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.dto.request.OAuthUserSignupRequest;
import io.github.team404.tikitaka.user.dto.response.OAuthUserSignupResponse;
import io.github.team404.tikitaka.user.repository.UserRepository;
import io.github.team404.tikitaka.user.service.UserSignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuthUserSignupServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserSignupService oauthUserSignupService;

    @BeforeEach
    void setUp() {
        oauthUserSignupService =
                new UserSignupService(userRepository);
    }

    @Test
    void 신규_사용자는_저장된다() {
        // given
        OAuthUserSignupRequest request =
                new OAuthUserSignupRequest(
                        "google-sub-123",
                        "test@gmail.com",
                        "테스트유저"
                );

        given(userRepository.findByProviderAndProviderId(
                OAuthProvider.GOOGLE,
                "google-sub-123"
        )).willReturn(Optional.empty());

        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        OAuthUserSignupResponse response =
                oauthUserSignupService.signupIfAbsent(request);

        // then
        assertThat(response.getEmail())
                .isEqualTo("test@gmail.com");

        assertThat(response.getName())
                .isEqualTo("테스트유저");

        verify(userRepository).save(any(User.class));
    }

    @Test
    void 기존_사용자는_다시_저장되지_않는다() {
        // given
        OAuthUserSignupRequest request =
                new OAuthUserSignupRequest(
                        "google-sub-123",
                        "test@gmail.com",
                        "테스트유저"
                );

        User existingUser = new User(
                "test@gmail.com",
                "테스트유저",
                OAuthProvider.GOOGLE,
                "google-sub-123"
        );

        given(userRepository.findByProviderAndProviderId(
                OAuthProvider.GOOGLE,
                "google-sub-123"
        )).willReturn(Optional.of(existingUser));

        // when
        OAuthUserSignupResponse response =
                oauthUserSignupService.signupIfAbsent(request);

        // then
        assertThat(response.getEmail())
                .isEqualTo("test@gmail.com");

        verify(userRepository, never())
                .save(any(User.class));
    }

    @Test
    void providerId로_조회되지_않지만_이메일이_중복되면_예외가_전파된다() {
        // given: 동일 이메일을 사용하는 다른 providerId의 사용자가 이미 존재하는 상황을 가정
        OAuthUserSignupRequest request =
                new OAuthUserSignupRequest(
                        "google-sub-999",
                        "test@gmail.com",
                        "테스트유저"
                );

        given(userRepository.findByProviderAndProviderId(
                OAuthProvider.GOOGLE,
                "google-sub-999"
        )).willReturn(Optional.empty());

        given(userRepository.save(any(User.class)))
                .willThrow(new DataIntegrityViolationException("email unique constraint violated"));

        // when & then: 임의로 기존 이메일 사용자에 연결하지 않고, 예외를 그대로 전파한다.
        assertThatThrownBy(() -> oauthUserSignupService.signupIfAbsent(request))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(userRepository).save(any(User.class));
    }
}