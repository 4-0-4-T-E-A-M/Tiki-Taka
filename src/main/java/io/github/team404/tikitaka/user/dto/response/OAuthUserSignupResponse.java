package io.github.team404.tikitaka.user.dto.response;

import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OAuthUserSignupResponse {
    private Long userId;
    private String email;
    private String name;
    private OAuthProvider provider;
    private UserRole role;

    public static OAuthUserSignupResponse from(User user){
        return new OAuthUserSignupResponse(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                user.getProvider(),
                user.getRole()
        );
    }
}