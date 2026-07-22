package io.github.team404.tikitaka.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public User(
            String email,
            String name,
            OAuthProvider provider,
            String providerId
    ) {
        this.email = email;
        this.name = name;
        this.provider = provider;
        this.providerId = providerId;
    }

    public void updateRole(UserRole role) {
        if (role == null) {
            throw new IllegalArgumentException("사용자 권한은 null일 수 없습니다.");
        }
        this.role = role;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}