package com.studentorgs.portal.content.service;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageRequests;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.content.domain.Announcement;
import com.studentorgs.portal.content.dto.AnnouncementRequest;
import com.studentorgs.portal.content.dto.AnnouncementResponse;
import com.studentorgs.portal.content.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;

/** Announcements: publish (then notify all active users), list, delete. */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final Sort NEWEST_FIRST = Sort.by("publishedAt").descending().and(Sort.by("id").descending());

    private final AnnouncementRepository announcements;
    private final MemberGuard guard;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public AnnouncementResponse publish(Long officerId, AnnouncementRequest request) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        Announcement announcement = announcements.save(new Announcement(request.title().trim(), request.body().trim(),
                officerId, clock.instant()));
        events.publishEvent(new AnnouncementPublishedEvent(announcement.getId(), announcement.getTitle()));
        return AnnouncementResponse.from(announcement);
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> list(Long userId, Integer page, Integer size) {
        guard.requireActive(userId);
        return PageResponse.from(announcements.findAll(PageRequests.of(page, size, NEWEST_FIRST)),
                AnnouncementResponse::from);
    }

    /** The publisher or any officer_admin may delete. */
    @Transactional
    public void delete(Long userId, Long announcementId) {
        UserStatusInfo caller = guard.requireActive(userId);
        Announcement announcement = announcements.findById(announcementId)
                .orElseThrow(() -> new NotFoundException("Announcement " + announcementId + " not found"));
        if (!announcement.getPublishedBy().equals(userId) && caller.role() != Role.OFFICER_ADMIN) {
            throw new ForbiddenOperationException("Only the publisher or an officer_admin can delete this announcement");
        }
        announcements.delete(announcement);
    }
}
