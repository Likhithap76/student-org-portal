package com.studentorgs.portal.content;

import java.time.Instant;

import org.springframework.test.util.ReflectionTestUtils;

import com.studentorgs.portal.common.security.AuthenticatedUser;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.content.domain.Announcement;
import com.studentorgs.portal.content.domain.DocumentLink;

/** Test fixtures for content-service. */
public final class TestContent {

    public static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");

    private TestContent() {
    }

    public static Announcement announcement(long id, long publisher) {
        Announcement a = new Announcement("Title " + id, "Body", publisher, NOW);
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    public static DocumentLink document(long id, long creator) {
        DocumentLink d = new DocumentLink("Doc " + id, "Forms", "https://example.edu/" + id, creator, NOW);
        ReflectionTestUtils.setField(d, "id", id);
        return d;
    }

    public static String bearer(JwtService jwtService, long id, Role role) {
        return "Bearer " + jwtService.issue(new AuthenticatedUser(id, "u" + id + "@uta.edu", "U" + id, role, UserStatus.ACTIVE));
    }
}
