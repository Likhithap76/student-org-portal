package com.studentorgs.portal.task.dto;

import com.studentorgs.portal.task.domain.TaskStatus;
import jakarta.validation.constraints.NotNull;

public record TaskStatusRequest(@NotNull TaskStatus status) {
}
