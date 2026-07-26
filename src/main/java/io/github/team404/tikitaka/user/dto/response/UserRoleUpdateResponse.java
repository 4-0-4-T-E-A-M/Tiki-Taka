package io.github.team404.tikitaka.user.dto.response;

import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserRoleUpdateResponse {
    private Long userId;
    private String email;
    private UserRole role;

    public static UserRoleUpdateResponse from(User user) {
        return new UserRoleUpdateResponse(user.getUserId(), user.getEmail(), user.getRole());
    }
}
