package com.studentorgs.portal.task.service;

/** Published when a task is created or reassigned; handled after commit. */
public record TaskAssignedEvent(Long taskId, Long assigneeId, String title) {
}
