package com.studentorgs.portal.event.dto;

import com.studentorgs.portal.event.domain.RsvpChoice;
import jakarta.validation.constraints.NotNull;

public record RsvpRequest(@NotNull RsvpChoice response) {
}
