package com.studentorgs.portal.reporting.dto;

import java.time.Instant;

/** Leadership dashboard totals. */
public record SummaryResponse(
        long activeMembers,
        long pendingApplications,
        long upcomingEvents,
        long openTasks,
        long overdueTasks,
        Instant generatedAt) {
}
