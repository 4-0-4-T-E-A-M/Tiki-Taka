package io.github.team404.tikitaka.user.dto.response;

import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OAuthUserSignupResponse {
    private Long userId;
    private String email;
    private String name;
    private OAuthProvider provider;

    public static OAuthUserSignupResponse from(User user){
        return new OAuthUserSignupResponse(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getProvider()
        );
    }
}