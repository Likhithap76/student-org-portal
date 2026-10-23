package com.studentorgs.portal.task.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One accepted action item to turn into a task. */
public record TaskBatchItem(
        @NotBlank @Size(max = 200) String title,
        @NotNull Long assigneeId,
        LocalDate dueDate) {
}
