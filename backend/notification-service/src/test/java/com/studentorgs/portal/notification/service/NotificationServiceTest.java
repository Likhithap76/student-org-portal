package com.studentorgs.portal.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.notification.domain.Notification;
import com.studentorgs.portal.notification.dto.CreateNotificationsRequest;
import com.studentorgs.portal.notification.dto.NotificationResponse;
import com.studentorgs.portal.notification.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");

    @Mock
    private NotificationRepository notifications;
    @Mock
    private MemberGuard guard;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notifications, guard, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Notification notification(long id, long recipient) {
        Notification n = new Notification(recipient, NotificationKind.ANNOUNCEMENT, "Hello", "announcement", 9L, NOW);
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    @Test
    @SuppressWarnings("unchecked")
    void create_duplicateRecipients_oneRowEach() {
        int created = service.create(new CreateNotificationsRequest(List.of(2L, 3L, 2L),
                NotificationKind.TASK_ASSIGNED, " New task ", "task", 40L));

        assertThat(created).isEqualTo(2);
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(notifications).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Notification::getRecipientId).containsExactly(2L, 3L);
        assertThat(saved.getValue()).allSatisfy(n -> {
            assertThat(n.getMessage()).isEqualTo("New task");
            assertThat(n.getCreatedAt()).isEqualTo(NOW);
            assertThat(n.getReadAt()).isNull();
        });
    }

    @Test
    void mine_unreadOnly_usesUnreadQuery() {
        when(notifications.findByRecipientIdAndReadAtIsNull(eq(2L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification(1, 2)), PageRequest.of(0, 20), 1));

        var page = service.mine(2L, true, null, null);

        assertThat(page.content()).extracting(NotificationResponse::read).containsExactly(false);
        verify(notifications, never()).findByRecipientId(any(), any());
        verify(guard).requireNotInactive(2L);
    }

    @Test
    void mine_all_usesAllQuery() {
        when(notifications.findByRecipientId(eq(2L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(service.mine(2L, false, 0, 20).content()).isEmpty();
    }

    @Test
    void mine_inactiveAccount_throwsForbidden() {
        when(guard.requireNotInactive(2L)).thenThrow(new ForbiddenOperationException("inactive"));

        assertThatThrownBy(() -> service.mine(2L, false, null, null)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void unreadCount_returnsRepositoryCount() {
        when(notifications.countByRecipientIdAndReadAtIsNull(2L)).thenReturn(4L);

        assertThat(service.unreadCount(2L)).isEqualTo(4);
    }

    @Test
    void markRead_own_setsReadAtOnce() {
        Notification n = notification(1, 2);
        when(notifications.findByIdAndRecipientId(1L, 2L)).thenReturn(Optional.of(n));

        NotificationResponse first = service.markRead(2L, 1L);
        n.markRead(NOW.plusSeconds(60));

        assertThat(first.readAt()).isEqualTo(NOW);
        assertThat(n.getReadAt()).isEqualTo(NOW);
    }

    @Test
    void markRead_someoneElses_throwsNotFound() {
        when(notifications.findByIdAndRecipientId(1L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(3L, 1L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void markAllRead_returnsUpdatedCount() {
        when(notifications.markAllRead(2L, NOW)).thenReturn(3);

        assertThat(service.markAllRead(2L)).isEqualTo(3);
    }
}
