package com.studentorgs.portal.task.dto;

import com.studentorgs.portal.task.domain.CommitteeKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CommitteeRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull CommitteeKind kind,
        @Size(max = 1000) String description) {
}
