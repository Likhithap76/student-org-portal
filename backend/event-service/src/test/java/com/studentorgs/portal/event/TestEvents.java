package com.studentorgs.portal.event;

import java.time.Duration;
import java.time.Instant;

import org.springframework.test.util.ReflectionTestUtils;

import com.studentorgs.portal.common.security.AuthenticatedUser;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.event.domain.Event;

/** Test fixtures for event-service. */
public final class TestEvents {

    public static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");

    private TestEvents() {
    }

    /** A scheduled event with the given id that starts {@code startOffset} from NOW and lasts 2 hours. */
    public static Event event(long id, Duration startOffset) {
        Instant start = NOW.plus(startOffset);
        Event event = Event.schedule("Event " + id, "desc", "ERB 101", start, start.plus(Duration.ofHours(2)), 1L,
                NOW.minus(Duration.ofDays(7)));
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    public static String bearer(JwtService jwtService, long id, Role role) {
        return "Bearer " + jwtService.issue(new AuthenticatedUser(id, "u" + id + "@uta.edu", "U" + id, role, UserStatus.ACTIVE));
    }
}
