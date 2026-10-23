package com.studentorgs.portal.event.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create / update body. Times are ISO-8601 instants, e.g. {@code 2026-11-05T23:00:00Z}. */
public record EventRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @Size(max = 200) String location,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt) {
}
