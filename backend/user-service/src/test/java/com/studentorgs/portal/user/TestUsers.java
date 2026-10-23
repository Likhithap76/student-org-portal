package com.studentorgs.portal.user;

import java.time.Instant;

import org.springframework.test.util.ReflectionTestUtils;

import com.studentorgs.portal.common.security.AuthenticatedUser;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.user.domain.AppUser;
import com.studentorgs.portal.user.domain.MembershipApplication;

/** Test fixtures for user-service. */
public final class TestUsers {

    public static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");

    private TestUsers() {
    }

    public static AppUser user(long id, Role role, UserStatus status) {
        AppUser user = new AppUser("user" + id + "@uta.edu", "User " + id, "hash-" + id, role, status, NOW);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    public static MembershipApplication pendingApplication(long id, long userId) {
        MembershipApplication application = MembershipApplication.submit(userId, NOW);
        ReflectionTestUtils.setField(application, "id", id);
        return application;
    }

    public static String bearer(JwtService jwtService, long id, Role role, UserStatus status) {
        return "Bearer " + jwtService.issue(new AuthenticatedUser(id, "user" + id + "@uta.edu", "User " + id, role, status));
    }
}
