package com.studentorgs.portal.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.studentorgs.portal.common.client.NotificationClient;
import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.client.NotificationRequest;
import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.error.ServiceUnavailableException;

@ExtendWith(MockitoExtension.class)
class AnnouncementNotifierTest {

    @Mock
    private UserDirectoryClient users;
    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private AnnouncementNotifier notifier;

    @Test
    void onPublished_notifiesAllActiveUsersInOneCall() {
        when(users.activeIds()).thenReturn(List.of(1L, 2L, 3L));

        notifier.onPublished(new AnnouncementPublishedEvent(7L, "Meeting moved"));

        verify(notificationClient).send(new NotificationRequest(List.of(1L, 2L, 3L), NotificationKind.ANNOUNCEMENT,
                "New announcement: Meeting moved", "announcement", 7L));
    }

    @Test
    void onPublished_userServiceDown_logsAndDoesNotThrow() {
        when(users.activeIds()).thenThrow(new ServiceUnavailableException("User service unavailable", null));

        assertThatCode(() -> notifier.onPublished(new AnnouncementPublishedEvent(7L, "t"))).doesNotThrowAnyException();
        verify(notificationClient, never()).send(any());
    }

    @Test
    void message_longTitle_truncatedTo500() {
        String message = AnnouncementNotifier.message("x".repeat(600));

        assertThat(message).hasSize(500).endsWith("…");
    }
}
