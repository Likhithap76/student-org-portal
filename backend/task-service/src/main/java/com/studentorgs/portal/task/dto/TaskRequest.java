package com.studentorgs.portal.task.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create body. {@code dueDate} is optional, e.g. {@code 2026-11-05}. */
public record TaskRequest(
        @NotNull Long committeeId,
        @NotBlank @Size(max = 200) String title,
        @NotNull Long assigneeId,
        LocalDate dueDate) {
}
