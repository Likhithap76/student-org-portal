package com.studentorgs.portal.event.service;

import static com.studentorgs.portal.event.TestEvents.NOW;
import static com.studentorgs.portal.event.TestEvents.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.event.domain.Event;
import com.studentorgs.portal.event.domain.Rsvp;
import com.studentorgs.portal.event.domain.RsvpChoice;
import com.studentorgs.portal.event.domain.RsvpId;
import com.studentorgs.portal.event.dto.RsvpEntryResponse;
import com.studentorgs.portal.event.dto.RsvpRequest;
import com.studentorgs.portal.event.repository.RsvpRepository;

@ExtendWith(MockitoExtension.class)
class RsvpServiceTest {

    @Mock
    private EventService eventService;
    @Mock
    private RsvpRepository rsvps;
    @Mock
    private MemberGuard guard;
    @Mock
    private UserDirectoryClient users;

    private RsvpService service;

    @BeforeEach
    void setUp() {
        service = new RsvpService(eventService, rsvps, guard, users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void respond_firstTime_createsRsvp() {
        when(eventService.find(5L)).thenReturn(event(5, Duration.ofDays(1)));
        when(rsvps.findById(new RsvpId(5L, 2L))).thenReturn(Optional.empty());
        when(rsvps.save(any(Rsvp.class))).thenAnswer(inv -> inv.getArgument(0));

        RsvpEntryResponse response = service.respond(2L, 5L, new RsvpRequest(RsvpChoice.GOING));

        assertThat(response.response()).isEqualTo(RsvpChoice.GOING);
        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.updatedAt()).isEqualTo(NOW);
        verify(guard).requireActive(2L);
    }

    @Test
    void respond_existingRsvp_updatesSameRow() {
        Rsvp existing = new Rsvp(5L, 2L, RsvpChoice.GOING, NOW.minusSeconds(3600));
        when(eventService.find(5L)).thenReturn(event(5, Duration.ofDays(1)));
        when(rsvps.findById(new RsvpId(5L, 2L))).thenReturn(Optional.of(existing));
        when(rsvps.save(existing)).thenReturn(existing);

        service.respond(2L, 5L, new RsvpRequest(RsvpChoice.NOT_GOING));

        assertThat(existing.getResponse()).isEqualTo(RsvpChoice.NOT_GOING);
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void respond_cancelledEvent_throwsBusinessRule() {
        Event cancelled = event(5, Duration.ofDays(1));
        cancelled.cancel(NOW);
        when(eventService.find(5L)).thenReturn(cancelled);

        assertThatThrownBy(() -> service.respond(2L, 5L, new RsvpRequest(RsvpChoice.GOING)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cancelled");
        verify(rsvps, never()).save(any());
    }

    @Test
    void respond_eventStarted_throwsBusinessRule() {
        when(eventService.find(5L)).thenReturn(event(5, Duration.ZERO));

        assertThatThrownBy(() -> service.respond(2L, 5L, new RsvpRequest(RsvpChoice.GOING)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("started");
    }

    @Test
    void respond_pendingMember_throwsForbidden() {
        when(guard.requireActive(3L)).thenThrow(new ForbiddenOperationException("not active"));

        assertThatThrownBy(() -> service.respond(3L, 5L, new RsvpRequest(RsvpChoice.GOING)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void list_officer_returnsRsvpsWithNames() {
        when(eventService.find(5L)).thenReturn(event(5, Duration.ofDays(1)));
        when(rsvps.findByIdEventIdOrderByUpdatedAtAsc(5L)).thenReturn(List.of(new Rsvp(5L, 2L, RsvpChoice.GOING, NOW)));
        when(users.names(List.of(2L))).thenReturn(Map.of(2L, "Ana"));

        List<RsvpEntryResponse> list = service.list(1L, 5L);

        assertThat(list).extracting(RsvpEntryResponse::displayName).containsExactly("Ana");
        verify(guard).requireRole(1L, Role.OFFICER_ADMIN);
    }
}
