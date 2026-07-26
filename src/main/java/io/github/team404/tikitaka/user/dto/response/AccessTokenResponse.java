package io.github.team404.tikitaka.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccessTokenResponse {

    private String accessToken;
    private String tokenType;
    private long accessTokenExpiresIn;

    public static AccessTokenResponse of(String accessToken, long accessTokenExpiry) {
        return new AccessTokenResponse(accessToken, "Bearer", accessTokenExpiry / 1000);
    }
}
