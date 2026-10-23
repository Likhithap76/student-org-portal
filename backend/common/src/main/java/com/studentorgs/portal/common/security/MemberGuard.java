package com.studentorgs.portal.common.security;

import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.error.ForbiddenOperationException;

/**
 * "Active member" checks for services that don't own {@code app_user}: asks user-service for the
 * caller's current status and role, so a stale JWT cannot outlive a deactivation or demotion.
 */
public class MemberGuard {

    private final UserDirectoryClient users;

    public MemberGuard(UserDirectoryClient users) {
        this.users = users;
    }

    /** Any existing account that is not inactive (pending users allowed), e.g. for reading own notifications. */
    public UserStatusInfo requireNotInactive(Long userId) {
        UserStatusInfo info = users.status(userId)
                .orElseThrow(() -> new ForbiddenOperationException("Your account no longer exists"));
        if (info.status() == UserStatus.INACTIVE) {
            throw new ForbiddenOperationException("Your account is inactive");
        }
        return info;
    }

    public UserStatusInfo requireActive(Long userId) {
        UserStatusInfo info = requireNotInactive(userId);
        if (info.status() != UserStatus.ACTIVE) {
            throw new ForbiddenOperationException("Your membership is not active");
        }
        return info;
    }

    public UserStatusInfo requireRole(Long userId, Role required) {
        UserStatusInfo info = requireActive(userId);
        if (!info.role().includes(required)) {
            throw new ForbiddenOperationException("You do not have permission to do this");
        }
        return info;
    }
}
