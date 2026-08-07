package io.github.team404.tikitaka.user.controller;

import io.github.team404.tikitaka.global.response.BaseResponse;
import io.github.team404.tikitaka.user.dto.request.UserRoleUpdateRequest;
import io.github.team404.tikitaka.user.dto.response.UserRoleUpdateResponse;
import io.github.team404.tikitaka.user.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserRoleService userRoleService;

    @PatchMapping("/{userId}/role")
    public ResponseEntity<BaseResponse<UserRoleUpdateResponse>> updateRole(
            @PathVariable Long userId,
            @RequestBody UserRoleUpdateRequest request
    ) {
        if (request.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "role은 필수입니다.");
        }
        UserRoleUpdateResponse response = userRoleService.updateRole(userId, request);
        return ResponseEntity.ok(
                BaseResponse.success("사용자 권한 변경에 성공했습니다.", response));
    }
}
