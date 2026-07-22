package io.github.team404.tikitaka.userTest.domain;

import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.domain.UserRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void 신규_OAuth_사용자의_기본_권한은_USER다() {
        User user = new User("user@example.com", "사용자", OAuthProvider.GOOGLE, "provider-id");

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void 사용자의_권한을_ADMIN으로_변경할_수_있다() {
        User user = new User("user@example.com", "사용자", OAuthProvider.GOOGLE, "provider-id");

        user.updateRole(UserRole.ADMIN);

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void 사용자_권한을_null로_변경할_수_없다() {
        User user = new User("user@example.com", "사용자", OAuthProvider.GOOGLE, "provider-id");

        assertThatThrownBy(() -> user.updateRole(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
