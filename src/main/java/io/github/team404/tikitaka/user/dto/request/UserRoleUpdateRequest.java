package io.github.team404.tikitaka.user.dto.request;

import io.github.team404.tikitaka.user.domain.UserRole;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
public class UserRoleUpdateRequest {
    @Setter
    private UserRole role;
}
