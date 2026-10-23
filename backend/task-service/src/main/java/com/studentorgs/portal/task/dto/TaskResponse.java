package com.studentorgs.portal.task.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.studentorgs.portal.task.domain.Task;
import com.studentorgs.portal.task.domain.TaskStatus;

/** A task. {@code overdue}: not done and the due date is before today in the organization's time zone. */
public record TaskResponse(
        Long id,
        Long committeeId,
        String title,
        Long assigneeId,
        LocalDate dueDate,
        TaskStatus status,
        Instant completedAt,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt,
        boolean overdue) {

    public static TaskResponse from(Task task, LocalDate today) {
        return new TaskResponse(task.getId(), task.getCommitteeId(), task.getTitle(), task.getAssigneeId(),
                task.getDueDate(), task.getStatus(), task.getCompletedAt(), task.getCreatedBy(), task.getCreatedAt(),
                task.getUpdatedAt(), task.isOverdue(today));
    }
}
