package com.studentorgs.portal.event.dto;

import com.studentorgs.portal.event.domain.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record AttendanceEntryRequest(@NotNull Long userId, @NotNull AttendanceStatus status) {
}
