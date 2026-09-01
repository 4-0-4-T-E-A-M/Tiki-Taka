package io.github.team404.tikitaka.user.repository;

import io.github.team404.tikitaka.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepositoryCustom {

    // email/name/role/provider를 선택적으로 조합해 조회한다 (조건이 전부 null이면 전체 조회).
    Page<User> search(UserSearchCondition condition, Pageable pageable);
}
