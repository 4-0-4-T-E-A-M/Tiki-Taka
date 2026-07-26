package io.github.team404.tikitaka.security.oauth2;

import io.github.team404.tikitaka.global.security.oauth2.CustomOAuth2User;
import io.github.team404.tikitaka.global.security.oauth2.CustomOAuth2UserService;
import io.github.team404.tikitaka.user.dto.request.OAuthUserSignupRequest;
import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.UserRole;
import io.github.team404.tikitaka.user.dto.response.OAuthUserSignupResponse;
import io.github.team404.tikitaka.user.service.UserSignupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.client.RestOperations;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserSignupService userSignupService;

    @Mock
    private RestOperations restOperations;

    private CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        customOAuth2UserService = new CustomOAuth2UserService(userSignupService);
        customOAuth2UserService.setRestOperations(restOperations);
    }

    @Test
    void 신규_Google_사용자는_UserSignupService를_통해_등록되고_Principal이_반환된다() {
        // given
        mockGoogleUserInfoResponse(Map.of(
                "sub", "google-sub-123",
                "email", "test@gmail.com",
                "name", "테스트유저"
        ));
        given(userSignupService.signupIfAbsent(any(OAuthUserSignupRequest.class)))
                .willReturn(new OAuthUserSignupResponse(1L, "test@gmail.com", "테스트유저", OAuthProvider.GOOGLE, UserRole.USER));

        // when
        OAuth2User result = customOAuth2UserService.loadUser(buildUserRequest());

        // then
        assertThat(result).isInstanceOf(CustomOAuth2User.class);
        assertThat(((CustomOAuth2User) result).getUserId()).isEqualTo(1L);
        verify(userSignupService).signupIfAbsent(any(OAuthUserSignupRequest.class));
    }

    @Test
    void 기존_사용자도_동일한_흐름으로_Principal이_반환된다() {
        // given
        mockGoogleUserInfoResponse(Map.of(
                "sub", "google-sub-existing",
                "email", "existing@gmail.com",
                "name", "기존유저"
        ));
        given(userSignupService.signupIfAbsent(any(OAuthUserSignupRequest.class)))
                .willReturn(new OAuthUserSignupResponse(7L, "existing@gmail.com", "기존유저", OAuthProvider.GOOGLE, UserRole.USER));

        // when
        OAuth2User result = customOAuth2UserService.loadUser(buildUserRequest());

        // then
        assertThat(((CustomOAuth2User) result).getUserId()).isEqualTo(7L);
    }

    @Test
    void Google의_sub가_providerId로_전달된다() {
        // given
        mockGoogleUserInfoResponse(Map.of(
                "sub", "google-sub-999",
                "email", "test@gmail.com",
                "name", "테스트유저"
        ));
        given(userSignupService.signupIfAbsent(any(OAuthUserSignupRequest.class)))
                .willReturn(new OAuthUserSignupResponse(1L, "test@gmail.com", "테스트유저", OAuthProvider.GOOGLE, UserRole.USER));

        // when
        customOAuth2UserService.loadUser(buildUserRequest());

        // then
        ArgumentCaptor<OAuthUserSignupRequest> captor = ArgumentCaptor.forClass(OAuthUserSignupRequest.class);
        verify(userSignupService).signupIfAbsent(captor.capture());
        assertThat(captor.getValue().getProviderId()).isEqualTo("google-sub-999");
    }

    @Test
    void 필수_정보인_email이_없으면_OAuth2AuthenticationException이_발생한다() {
        // given
        mockGoogleUserInfoResponse(Map.of(
                "sub", "google-sub-123",
                "name", "테스트유저"
        ));

        // when & then
        assertThatThrownBy(() -> customOAuth2UserService.loadUser(buildUserRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void 필수_정보인_name이_없으면_OAuth2AuthenticationException이_발생한다() {
        // given
        mockGoogleUserInfoResponse(Map.of(
                "sub", "google-sub-123",
                "email", "test@gmail.com"
        ));

        // when & then
        assertThatThrownBy(() -> customOAuth2UserService.loadUser(buildUserRequest()))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @SuppressWarnings("unchecked")
    private void mockGoogleUserInfoResponse(Map<String, Object> attributes) {
        given(restOperations.exchange(any(RequestEntity.class), any(ParameterizedTypeReference.class)))
                .willReturn(ResponseEntity.ok(attributes));
    }

    private OAuth2UserRequest buildUserRequest() {
        ClientRegistration clientRegistration = ClientRegistration.withRegistrationId("google")
                .clientId("test-client-id")
                .clientSecret("test-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/google")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://www.googleapis.com/oauth2/v4/token")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .scope("email", "profile")
                .clientName("Google")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        return new OAuth2UserRequest(clientRegistration, accessToken);
    }
}
