package com.studentorgs.portal.user.dto;

import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;

/** Internal: a user's current status and role, used by other services' "active member" checks. */
public record UserStatusResponse(UserStatus status, Role role) {

    public static UserStatusResponse from(AppUser user) {
        return new UserStatusResponse(user.getStatus(), user.getRole());
    }
}
