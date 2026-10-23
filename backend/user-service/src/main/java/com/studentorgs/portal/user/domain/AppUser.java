package com.studentorgs.portal.user.domain;

import java.time.Instant;

import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A portal account ({@code app_user}). Never deleted; deactivation sets status to inactive. */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Convert(converter = RoleConverter.class)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Convert(converter = UserStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AppUser(String email, String displayName, String passwordHash, Role role, UserStatus status,
            Instant createdAt) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
    }

    /** A newly registered user: member, pending approval. */
    public static AppUser newApplicant(String email, String displayName, String passwordHash, Instant now) {
        return new AppUser(email, displayName, passwordHash, Role.MEMBER, UserStatus.PENDING, now);
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public void changeDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
    }
}
