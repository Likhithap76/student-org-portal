package com.studentorgs.portal.content.dto;

import java.time.Instant;

import com.studentorgs.portal.content.domain.DocumentLink;

public record DocumentResponse(Long id, String title, String category, String url, Long createdBy, Instant createdAt,
        Instant updatedAt) {

    public static DocumentResponse from(DocumentLink d) {
        return new DocumentResponse(d.getId(), d.getTitle(), d.getCategory(), d.getUrl(), d.getCreatedBy(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
