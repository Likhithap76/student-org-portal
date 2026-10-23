package com.studentorgs.portal.content.service;

/** Published when an announcement is created; handled after commit. */
public record AnnouncementPublishedEvent(Long announcementId, String title) {
}
