package io.github.team404.tikitaka.user.repository;

import static io.github.team404.tikitaka.user.domain.QUser.user;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.github.team404.tikitaka.user.domain.OAuthProvider;
import io.github.team404.tikitaka.user.domain.User;
import io.github.team404.tikitaka.user.domain.UserRole;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

public class UserRepositoryImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public UserRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Page<User> search(UserSearchCondition condition, Pageable pageable) {
        List<User> content = queryFactory
                .selectFrom(user)
                .where(
                        emailEq(condition.email()),
                        nameContains(condition.name()),
                        roleEq(condition.role()),
                        providerEq(condition.provider()))
                .orderBy(user.createdAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        return PageableExecutionUtils.getPage(content, pageable, () -> queryFactory
                .select(user.count())
                .from(user)
                .where(
                        emailEq(condition.email()),
                        nameContains(condition.name()),
                        roleEq(condition.role()),
                        providerEq(condition.provider()))
                .fetchOne());
    }

    private BooleanExpression emailEq(String email) {
        return email != null && !email.isBlank() ? user.email.eq(email) : null;
    }

    private BooleanExpression nameContains(String name) {
        return name != null && !name.isBlank() ? user.name.contains(name) : null;
    }

    private BooleanExpression roleEq(UserRole role) {
        return role != null ? user.role.eq(role) : null;
    }

    private BooleanExpression providerEq(OAuthProvider provider) {
        return provider != null ? user.provider.eq(provider) : null;
    }
}
