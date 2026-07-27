package io.github.team404.tikitaka.global.security.principal;

import lombok.Getter;

@Getter
public class CustomUserPrincipal {

    private final Long userId;

    public CustomUserPrincipal(Long userId) {
        this.userId = userId;
    }
}
