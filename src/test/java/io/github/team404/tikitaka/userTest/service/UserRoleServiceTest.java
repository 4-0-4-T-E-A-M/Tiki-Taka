package io.github.team404.tikitaka.userTest.service;

import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.domain.UserRole;
import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.dto.request.UserRoleUpdateRequest;
import io.github.team404.tikitaka.user.dto.response.UserRoleUpdateResponse;
import io.github.team404.tikitaka.user.repository.UserRepository;
import io.github.team404.tikitaka.user.service.UserRoleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void 사용자의_권한을_변경하고_변경된_권한을_응답한다() {
        User user = new User("user@example.com", "사용자", OAuthProvider.GOOGLE, "provider-id");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        UserRoleUpdateRequest request = new UserRoleUpdateRequest();
        request.setRole(UserRole.ADMIN);

        UserRoleUpdateResponse response = new UserRoleService(userRepository).updateRole(1L, request);

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void 존재하지_않는_사용자는_예외가_발생한다() {
        given(userRepository.findById(99L)).willReturn(Optional.empty());
        UserRoleUpdateRequest request = new UserRoleUpdateRequest();
        request.setRole(UserRole.ADMIN);

        assertThatThrownBy(() -> new UserRoleService(userRepository).updateRole(99L, request))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }
}
