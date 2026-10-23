package com.studentorgs.portal.common.security;

/** The caller, as read from a validated JWT. Used as the Spring Security principal. */
public record AuthenticatedUser(Long id, String email, String name, Role role, UserStatus status) {

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
