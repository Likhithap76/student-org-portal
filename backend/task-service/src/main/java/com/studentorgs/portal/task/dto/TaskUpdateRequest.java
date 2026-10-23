package com.studentorgs.portal.task.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Edit / reassign body. The committee cannot change. A null {@code dueDate} clears it. */
public record TaskUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull Long assigneeId,
        LocalDate dueDate) {
}
