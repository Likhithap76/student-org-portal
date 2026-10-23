package com.studentorgs.portal.content.dto;

import java.time.Instant;

import com.studentorgs.portal.content.domain.Announcement;

public record AnnouncementResponse(Long id, String title, String body, Long publishedBy, Instant publishedAt) {

    public static AnnouncementResponse from(Announcement a) {
        return new AnnouncementResponse(a.getId(), a.getTitle(), a.getBody(), a.getPublishedBy(), a.getPublishedAt());
    }
}
