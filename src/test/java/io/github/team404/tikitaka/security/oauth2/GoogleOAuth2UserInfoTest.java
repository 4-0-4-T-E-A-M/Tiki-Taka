package io.github.team404.tikitaka.security.oauth2;

import io.github.team404.tikitaka.global.security.oauth2.GoogleOAuth2UserInfo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOAuth2UserInfoTest {

    @Test
    void Google_속성에서_providerId_email_name을_추출할_수_있다() {
        Map<String, Object> attributes = Map.of(
                "sub", "google-provider-id-123",
                "email", "test@gmail.com",
                "name", "테스트 사용자"
        );

        GoogleOAuth2UserInfo userInfo = new GoogleOAuth2UserInfo(attributes);

        assertThat(userInfo.getProviderId()).isEqualTo("google-provider-id-123");
        assertThat(userInfo.getEmail()).isEqualTo("test@gmail.com");
        assertThat(userInfo.getName()).isEqualTo("테스트 사용자");
    }
}
