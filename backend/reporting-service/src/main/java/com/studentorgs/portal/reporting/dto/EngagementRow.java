package com.studentorgs.portal.reporting.dto;

import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;

/** One member's engagement in the report period. */
public record EngagementRow(
        Long userId,
        String displayName,
        String email,
        Role role,
        UserStatus status,
        long eventsAttended,
        long tasksCompleted) {
}
