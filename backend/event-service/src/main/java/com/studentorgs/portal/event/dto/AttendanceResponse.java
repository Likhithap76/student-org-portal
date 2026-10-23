package com.studentorgs.portal.event.dto;

import java.util.List;

/**
 * Attendance for one event. {@code rate = presentCount / goingCount} (spec §6.3), rounded to 4 decimals;
 * null when nobody RSVP'd "going". It can exceed 1.0 when people attend without RSVPing.
 */
public record AttendanceResponse(
        Long eventId,
        List<AttendanceEntryResponse> entries,
        long presentCount,
        long goingCount,
        Double rate) {
}
