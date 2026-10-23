package com.studentorgs.portal.content.service;

import static com.studentorgs.portal.content.TestContent.NOW;
import static com.studentorgs.portal.content.TestContent.announcement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.content.domain.Announcement;
import com.studentorgs.portal.content.dto.AnnouncementRequest;
import com.studentorgs.portal.content.dto.AnnouncementResponse;
import com.studentorgs.portal.content.repository.AnnouncementRepository;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock
    private AnnouncementRepository announcements;
    @Mock
    private MemberGuard guard;
    @Mock
    private ApplicationEventPublisher events;

    private AnnouncementService service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementService(announcements, guard, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UserStatusInfo active(Role role) {
        return new UserStatusInfo(UserStatus.ACTIVE, role);
    }

    @Test
    void publish_officer_savesAndPublishesEvent() {
        when(announcements.save(any(Announcement.class))).thenAnswer(inv -> {
            Announcement a = inv.getArgument(0);
            ReflectionTestUtils.setField(a, "id", 7L);
            return a;
        });

        AnnouncementResponse response = service.publish(1L, new AnnouncementRequest(" Meeting moved ", " Room 200 "));

        assertThat(response.title()).isEqualTo("Meeting moved");
        assertThat(response.body()).isEqualTo("Room 200");
        assertThat(response.publishedBy()).isEqualTo(1L);
        assertThat(response.publishedAt()).isEqualTo(NOW);
        verify(guard).requireRole(1L, Role.OFFICER_ADMIN);
        verify(events).publishEvent(new AnnouncementPublishedEvent(7L, "Meeting moved"));
    }

    @Test
    void publish_notOfficer_throwsForbiddenAndSavesNothing() {
        when(guard.requireRole(2L, Role.OFFICER_ADMIN)).thenThrow(new ForbiddenOperationException("no"));

        assertThatThrownBy(() -> service.publish(2L, new AnnouncementRequest("t", "b")))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(announcements, never()).save(any());
    }

    @Test
    void list_member_returnsPage() {
        when(announcements.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(announcement(7, 1)), PageRequest.of(0, 20), 1));

        assertThat(service.list(2L, null, null).content()).extracting(AnnouncementResponse::id).containsExactly(7L);
        verify(guard).requireActive(2L);
    }

    @Test
    void delete_publisher_deletes() {
        Announcement a = announcement(7, 1);
        when(guard.requireActive(1L)).thenReturn(active(Role.OFFICER_ADMIN));
        when(announcements.findById(7L)).thenReturn(Optional.of(a));

        service.delete(1L, 7L);

        verify(announcements).delete(a);
    }

    @Test
    void delete_otherOfficerAdmin_deletesSomeoneElses() {
        Announcement a = announcement(7, 1);
        when(guard.requireActive(9L)).thenReturn(active(Role.OFFICER_ADMIN));
        when(announcements.findById(7L)).thenReturn(Optional.of(a));

        service.delete(9L, 7L);

        verify(announcements).delete(a);
    }

    @Test
    void delete_memberNotPublisher_throwsForbidden() {
        when(guard.requireActive(3L)).thenReturn(active(Role.MEMBER));
        when(announcements.findById(7L)).thenReturn(Optional.of(announcement(7, 1)));

        assertThatThrownBy(() -> service.delete(3L, 7L)).isInstanceOf(ForbiddenOperationException.class);
        verify(announcements, never()).delete(any());
    }

    @Test
    void delete_unknown_throwsNotFound() {
        when(guard.requireActive(1L)).thenReturn(active(Role.OFFICER_ADMIN));
        when(announcements.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 7L)).isInstanceOf(NotFoundException.class);
    }
}
