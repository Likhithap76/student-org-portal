package com.studentorgs.portal.common.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Static access to the authenticated caller. Controllers read it and pass ids into services. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthenticatedUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return user;
    }

    public static Long id() {
        return get().id();
    }

    public static Role role() {
        return get().role();
    }

    public static boolean hasRole(Role required) {
        return get().role().includes(required);
    }

    public static boolean isActive() {
        return get().isActive();
    }
}
