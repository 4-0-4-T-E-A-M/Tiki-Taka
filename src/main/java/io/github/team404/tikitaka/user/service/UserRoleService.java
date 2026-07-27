package io.github.team404.tikitaka.user.service;

import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.dto.request.UserRoleUpdateRequest;
import io.github.team404.tikitaka.user.dto.response.UserRoleUpdateResponse;
import io.github.team404.tikitaka.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRoleService {
    private final UserRepository userRepository;

    @Transactional
    public UserRoleUpdateResponse updateRole(Long userId, UserRoleUpdateRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        user.updateRole(request.getRole());
        return UserRoleUpdateResponse.from(user);
    }
}
