package io.github.team404.tikitaka.user.dto.request;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OAuthUserSignupRequest {

    private String providerId;
    private String email;
    private String name;
}
