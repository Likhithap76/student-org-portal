package com.studentorgs.portal.event.service;

import static com.studentorgs.portal.event.TestEvents.NOW;
import static com.studentorgs.portal.event.TestEvents.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.event.domain.Attendance;
import com.studentorgs.portal.event.domain.AttendanceStatus;
import com.studentorgs.portal.event.domain.Event;
import com.studentorgs.portal.event.domain.RsvpChoice;
import com.studentorgs.portal.event.dto.AttendanceEntryRequest;
import com.studentorgs.portal.event.dto.AttendanceResponse;
import com.studentorgs.portal.event.repository.AttendanceRepository;
import com.studentorgs.portal.event.repository.RsvpRepository;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final long OFFICER_ADMIN_ID = 1L;

    @Mock
    private EventService eventService;
    @Mock
    private AttendanceRepository attendance;
    @Mock
    private RsvpRepository rsvps;
    @Mock
    private MemberGuard guard;
    @Mock
    private UserDirectoryClient users;

    private AttendanceService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(eventService, attendance, rsvps, guard, users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static List<AttendanceEntryRequest> entries(Object... pairs) {
        return java.util.stream.IntStream.range(0, pairs.length / 2)
                .mapToObj(i -> new AttendanceEntryRequest((Long) pairs[2 * i], (AttendanceStatus) pairs[2 * i + 1]))
                .toList();
    }

    @Test
    void record_newRows_savesWithRecorderAndTime() {
        Event started = event(5, Duration.ofMinutes(-10));
        when(eventService.find(5L)).thenReturn(started);
        when(users.names(Set.of(2L, 3L))).thenReturn(Map.of(2L, "Ana", 3L, "Bob"));
        when(attendance.findByIdEventIdAndIdUserIdIn(5L, Set.of(2L, 3L))).thenReturn(List.of());

        service.record(OFFICER_ADMIN_ID, 5L, entries(2L, AttendanceStatus.PRESENT, 3L, AttendanceStatus.ABSENT));

        ArgumentCaptor<Attendance> saved = ArgumentCaptor.forClass(Attendance.class);
        verify(attendance, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(a -> {
            assertThat(a.getRecordedBy()).isEqualTo(OFFICER_ADMIN_ID);
            assertThat(a.getRecordedAt()).isEqualTo(NOW);
        });
        verify(guard).requireRole(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN);
    }

    @Test
    void record_existingRow_updatesInPlace() {
        Attendance existing = new Attendance(5L, 2L, AttendanceStatus.ABSENT, 9L, NOW.minusSeconds(600));
        when(eventService.find(5L)).thenReturn(event(5, Duration.ofMinutes(-10)));
        when(users.names(Set.of(2L))).thenReturn(Map.of(2L, "Ana"));
        when(attendance.findByIdEventIdAndIdUserIdIn(5L, Set.of(2L))).thenReturn(List.of(existing));

        service.record(OFFICER_ADMIN_ID, 5L, entries(2L, AttendanceStatus.PRESENT));

        assertThat(existing.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(existing.getRecordedBy()).isEqualTo(OFFICER_ADMIN_ID);
        verify(attendance, never()).save(any());
    }

    @Test
    void record_beforeStart_throwsBusinessRule() {
        when(eventService.find(5L)).thenReturn(event(5, Duration.ofMinutes(10)));

        assertThatThrownBy(() -> service.record(OFFICER_ADMIN_ID, 5L, entries(2L, AttendanceStatus.PRESENT)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("after the event starts");
    }

    @Test
    void record_cancelledEvent_throwsBusinessRule() {
        Event cancelled = event(5, Duration.ofMinutes(-10));
        cancelled.cancel(NOW);
        when(eventService.find(5L)).thenReturn(cancelled);

        assertThatThrownBy(() -> service.record(OFFICER_ADMIN_ID, 5L, entries(2L, AttendanceStatus.PRESENT)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cancelled");
    }

    @Test
    void record_duplicateUser_throwsBusinessRule() {
        when(eventService.find(5L)).thenReturn(event(5, Duration.ofMinutes(-10)));

        assertThatThrownBy(() -> service.record(OFFICER_ADMIN_ID, 5L,
                entries(2L, AttendanceStatus.PRESENT, 2L, AttendanceStatus.ABSENT)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("once");
    }

    @Test
    void record_unknownUser_throwsBusinessRuleListingIds() {
        when(eventService.find(5L)).thenReturn(event(5, Duration.ofMinutes(-10)));
        when(users.names(Set.of(2L, 99L))).thenReturn(Map.of(2L, "Ana"));

        assertThatThrownBy(() -> service.record(OFFICER_ADMIN_ID, 5L,
                entries(2L, AttendanceStatus.PRESENT, 99L, AttendanceStatus.PRESENT)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("99");
        verify(attendance, never()).save(any());
    }

    @Test
    void summary_presentAndGoing_computesRate() {
        when(eventService.find(5L)).thenReturn(event(5, Duration.ofMinutes(-10)));
        when(attendance.findByIdEventId(5L)).thenReturn(List.of(
                new Attendance(5L, 2L, AttendanceStatus.PRESENT, 1L, NOW),
                new Attendance(5L, 3L, AttendanceStatus.ABSENT, 1L, NOW)));
        when(users.names(anyCollection())).thenReturn(Map.of(2L, "Ana", 3L, "Bob"));
        when(rsvps.countByIdEventIdAndResponse(5L, RsvpChoice.GOING)).thenReturn(3L);

        AttendanceResponse response = service.summary(OFFICER_ADMIN_ID, 5L);

        assertThat(response.presentCount()).isEqualTo(1);
        assertThat(response.goingCount()).isEqualTo(3);
        assertThat(response.rate()).isEqualTo(0.3333);
        assertThat(response.entries()).extracting("displayName").containsExactly("Ana", "Bob");
    }

    @Test
    void rate_nobodyGoing_isNull() {
        assertThat(AttendanceService.rate(2, 0)).isNull();
    }

    @Test
    void rate_moreThanGoing_canExceedOne() {
        assertThat(AttendanceService.rate(3, 2)).isEqualTo(1.5);
    }
}
