package com.studentorgs.portal.reporting.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** FR-07 engagement report. {@code from} and {@code to} are inclusive dates in the organization's time zone. */
public record EngagementReport(
        LocalDate from,
        LocalDate to,
        boolean includeInactive,
        Instant generatedAt,
        List<EngagementRow> rows) {
}
