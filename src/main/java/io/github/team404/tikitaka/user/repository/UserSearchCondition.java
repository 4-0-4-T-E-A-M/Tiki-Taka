package io.github.team404.tikitaka.user.repository;

import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.UserRole;

// 사용자 복합 검색 조건 - 모든 필드는 선택(null 허용)이며 null 또는 blank 문자열 조건은 무시된다.
public record UserSearchCondition(
        String email,
        String name,
        UserRole role,
        OAuthProvider provider
) {
}
