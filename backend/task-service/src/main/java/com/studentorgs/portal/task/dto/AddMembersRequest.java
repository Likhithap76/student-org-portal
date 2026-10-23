package com.studentorgs.portal.task.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddMembersRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> userIds) {
}
