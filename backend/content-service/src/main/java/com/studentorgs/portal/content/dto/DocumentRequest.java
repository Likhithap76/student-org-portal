package com.studentorgs.portal.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A document link. The URL must start with {@code https://} (checked by the service: 422). */
public record DocumentRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 50) String category,
        @NotBlank @Size(max = 2000) String url) {
}
