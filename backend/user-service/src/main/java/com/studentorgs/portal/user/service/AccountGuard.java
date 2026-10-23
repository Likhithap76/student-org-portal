package com.studentorgs.portal.user.service;

import org.springframework.stereotype.Component;

import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;
import com.studentorgs.portal.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Re-checks the caller against {@code app_user}, not just the JWT, so a role change or deactivation
 * takes effect at once in user-service even while an older token is still valid.
 */
@Component
@RequiredArgsConstructor
public class AccountGuard {

    private final AppUserRepository users;

    /** Any caller whose account still exists and is not inactive (pending users allowed). */
    public AppUser requireUsable(Long callerId) {
        AppUser caller = users.findById(callerId)
                .orElseThrow(() -> new ForbiddenOperationException("Your account no longer exists"));
        if (caller.getStatus() == UserStatus.INACTIVE) {
            throw new ForbiddenOperationException("Your account is inactive");
        }
        return caller;
    }

    /** An active member (or higher). */
    public AppUser requireActive(Long callerId) {
        AppUser caller = requireUsable(callerId);
        if (!caller.isActive()) {
            throw new ForbiddenOperationException("Your membership is not active yet");
        }
        return caller;
    }

    /** An active user whose current role includes {@code required}. */
    public AppUser requireRole(Long callerId, Role required) {
        AppUser caller = requireActive(callerId);
        if (!caller.getRole().includes(required)) {
            throw new ForbiddenOperationException("You do not have permission to do this");
        }
        return caller;
    }
}
