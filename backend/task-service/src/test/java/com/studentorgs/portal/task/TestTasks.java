package com.studentorgs.portal.task;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.test.util.ReflectionTestUtils;

import com.studentorgs.portal.common.security.AuthenticatedUser;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.task.domain.Committee;
import com.studentorgs.portal.task.domain.CommitteeKind;
import com.studentorgs.portal.task.domain.Task;

/** Test fixtures for task-service. NOW is 10:00 in Chicago on 2026-10-20. */
public final class TestTasks {

    public static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    private TestTasks() {
    }

    public static Committee committee(long id) {
        Committee committee = new Committee("Committee " + id, CommitteeKind.COMMITTEE, null, NOW);
        ReflectionTestUtils.setField(committee, "id", id);
        return committee;
    }

    public static Task task(long id, long committeeId, long assigneeId, LocalDate dueDate) {
        Task task = Task.assign(committeeId, "Task " + id, assigneeId, dueDate, 1L, NOW.minusSeconds(86400));
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }

    public static String bearer(JwtService jwtService, long id, Role role) {
        return "Bearer " + jwtService.issue(new AuthenticatedUser(id, "u" + id + "@uta.edu", "U" + id, role, UserStatus.ACTIVE));
    }
}
