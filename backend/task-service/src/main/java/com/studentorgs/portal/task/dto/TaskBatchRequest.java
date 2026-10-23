package com.studentorgs.portal.task.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Several tasks in one committee, e.g. the action items an officer accepted from a meeting summary. */
public record TaskBatchRequest(
        @NotNull Long committeeId,
        @NotEmpty @Size(max = 20) List<@Valid @NotNull TaskBatchItem> items) {
}
