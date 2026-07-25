package io.github.team404.tikitaka.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true)
    private String email;

    // LOCAL 로그인 확장성을 위해 필드만 유지
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Builder
    private User(
            String email,
            String passwordHash,
            String name,
            OAuthProvider provider,
            String providerId
    ) {
        LocalDateTime now = LocalDateTime.now();

        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.provider = provider;
        this.providerId = providerId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    // 로그인 성공 시 마지막 로그인 시간과 사용자 수정 시간을 변경한다.
    public void updateLastLoginAt() {
        LocalDateTime now = LocalDateTime.now();

        this.lastLoginAt = now;
        this.updatedAt = now;
    }
}