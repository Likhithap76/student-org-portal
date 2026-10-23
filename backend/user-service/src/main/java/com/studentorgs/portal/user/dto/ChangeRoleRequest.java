package com.studentorgs.portal.user.dto;

import com.studentorgs.portal.common.security.Role;
import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(@NotNull Role role) {
}
