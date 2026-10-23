package com.studentorgs.portal.task.dto;

/** Internal: a committee member's id and display name (used by ai-service for assignee matching). */
public record MemberNameResponse(Long userId, String displayName) {
}
