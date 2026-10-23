package com.studentorgs.portal.content.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.studentorgs.portal.common.client.NotificationClient;
import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.client.NotificationRequest;
import com.studentorgs.portal.common.client.UserDirectoryClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * After an announcement commits, notifies every active user (one call to notification-service).
 * Any failure is logged and swallowed: the announcement stays published.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementNotifier {

    static final String REFERENCE_TABLE = "announcement";
    static final int MAX_MESSAGE = 500;

    private final UserDirectoryClient users;
    private final NotificationClient notificationClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPublished(AnnouncementPublishedEvent event) {
        List<Long> recipients;
        try {
            recipients = users.activeIds();
        } catch (RuntimeException e) {
            log.warn("Announcement {} published, but active users could not be loaded: {}",
                    event.announcementId(), e.getClass().getSimpleName());
            return;
        }
        notificationClient.send(new NotificationRequest(recipients, NotificationKind.ANNOUNCEMENT,
                message(event.title()), REFERENCE_TABLE, event.announcementId()));
    }

    static String message(String title) {
        String message = "New announcement: " + title;
        return message.length() <= MAX_MESSAGE ? message : message.substring(0, MAX_MESSAGE - 1) + "…";
    }
}
