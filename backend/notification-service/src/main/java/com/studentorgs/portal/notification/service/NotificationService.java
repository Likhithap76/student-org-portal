package com.studentorgs.portal.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageRequests;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.notification.domain.Notification;
import com.studentorgs.portal.notification.dto.CreateNotificationsRequest;
import com.studentorgs.portal.notification.dto.NotificationResponse;
import com.studentorgs.portal.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;

/**
 * Stores notifications sent by other services and serves each user their own.
 * Pending users may read theirs (e.g. an application decision); inactive accounts may not.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Sort NEWEST_FIRST = Sort.by("createdAt").descending().and(Sort.by("id").descending());

    private final NotificationRepository notifications;
    private final MemberGuard guard;
    private final Clock clock;

    /** One row per distinct recipient. */
    @Transactional
    public int create(CreateNotificationsRequest request) {
        Instant now = clock.instant();
        List<Notification> rows = request.recipientIds().stream().distinct()
                .map(id -> new Notification(id, request.kind(), request.message().trim(), request.referenceTable(),
                        request.referenceId(), now))
                .toList();
        notifications.saveAll(rows);
        return rows.size();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> mine(Long userId, boolean unreadOnly, Integer page, Integer size) {
        guard.requireNotInactive(userId);
        PageRequest pageable = PageRequests.of(page, size, NEWEST_FIRST);
        return PageResponse.from(unreadOnly
                ? notifications.findByRecipientIdAndReadAtIsNull(userId, pageable)
                : notifications.findByRecipientId(userId, pageable), NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        guard.requireNotInactive(userId);
        return notifications.countByRecipientIdAndReadAtIsNull(userId);
    }

    /** Someone else's notification looks the same as a missing one (404). */
    @Transactional
    public NotificationResponse markRead(Long userId, Long notificationId) {
        guard.requireNotInactive(userId);
        Notification notification = notifications.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("Notification " + notificationId + " not found"));
        notification.markRead(clock.instant());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public int markAllRead(Long userId) {
        guard.requireNotInactive(userId);
        return notifications.markAllRead(userId, clock.instant());
    }
}
