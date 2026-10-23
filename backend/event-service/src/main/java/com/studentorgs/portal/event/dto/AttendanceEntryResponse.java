package com.studentorgs.portal.event.dto;

import java.time.Instant;

import com.studentorgs.portal.event.domain.Attendance;
import com.studentorgs.portal.event.domain.AttendanceStatus;

public record AttendanceEntryResponse(Long userId, String displayName, AttendanceStatus status, Long recordedBy,
        Instant recordedAt) {

    public static AttendanceEntryResponse from(Attendance attendance, String displayName) {
        return new AttendanceEntryResponse(attendance.getId().getUserId(), displayName, attendance.getStatus(),
                attendance.getRecordedBy(), attendance.getRecordedAt());
    }
}
