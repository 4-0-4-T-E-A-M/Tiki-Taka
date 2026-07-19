package io.github.team404.tikitaka.global.security.oauth2;

import io.github.team404.tikitaka.user.dto.request.OAuthUserSignupRequest;
import io.github.team404.tikitaka.user.dto.response.OAuthUserSignupResponse;
import io.github.team404.tikitaka.user.service.UserSignupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserSignupService userSignupService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        GoogleOAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(oauth2User.getAttributes());
        validateRequiredFields(userInfo);

        OAuthUserSignupRequest request = new OAuthUserSignupRequest(
                userInfo.getProviderId(),
                userInfo.getEmail(),
                userInfo.getName()
        );

        try {
            OAuthUserSignupResponse response = userSignupService.signupIfAbsent(request);
            return new CustomOAuth2User(oauth2User, response.getUserId());
        } catch (Exception e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("USER_REGISTRATION_FAILED"), e.getMessage(), e
            );
        }
    }

    private void validateRequiredFields(GoogleOAuth2UserInfo userInfo) {
        if (isBlank(userInfo.getProviderId()) || isBlank(userInfo.getEmail()) || isBlank(userInfo.getName())) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("INVALID_USER_INFO"),
                    "Google 계정에서 필수 사용자 정보(providerId, email, name)를 가져오지 못했습니다."
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
