package com.studentorgs.portal.user.dto;

import java.time.Instant;

import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;

public record UserResponse(Long id, String email, String displayName, Role role, UserStatus status, Instant createdAt) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.getStatus(), user.getCreatedAt());
    }
}
