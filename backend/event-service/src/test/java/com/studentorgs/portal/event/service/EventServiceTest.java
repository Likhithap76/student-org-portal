package com.studentorgs.portal.event.service;

import static com.studentorgs.portal.event.TestEvents.NOW;
import static com.studentorgs.portal.event.TestEvents.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.event.domain.Event;
import com.studentorgs.portal.event.domain.EventStatus;
import com.studentorgs.portal.event.domain.Rsvp;
import com.studentorgs.portal.event.domain.RsvpChoice;
import com.studentorgs.portal.event.dto.EventRequest;
import com.studentorgs.portal.event.dto.EventResponse;
import com.studentorgs.portal.event.repository.EventRepository;
import com.studentorgs.portal.event.repository.RsvpRepository;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository events;
    @Mock
    private RsvpRepository rsvps;
    @Mock
    private MemberGuard guard;

    private EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService(events, rsvps, guard, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static EventRequest request(Instant start, Instant end) {
        return new EventRequest(" Kickoff ", " ", "ERB 101", start, end);
    }

    @Test
    void create_validFutureEvent_savesScheduledEvent() {
        when(events.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        EventResponse response = service.create(1L, request(NOW.plusSeconds(3600), NOW.plusSeconds(7200)));

        assertThat(response.title()).isEqualTo("Kickoff");
        assertThat(response.description()).isNull();
        assertThat(response.status()).isEqualTo(EventStatus.SCHEDULED);
        assertThat(response.createdBy()).isEqualTo(1L);
        assertThat(response.goingCount()).isZero();
        verify(guard).requireRole(1L, Role.OFFICER_ADMIN);
    }

    @Test
    void create_endBeforeStart_throwsBusinessRule() {
        assertThatThrownBy(() -> service.create(1L, request(NOW.plusSeconds(7200), NOW.plusSeconds(3600))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("endsAt");
        verify(events, never()).save(any());
    }

    @Test
    void create_endEqualsStart_throwsBusinessRule() {
        Instant t = NOW.plusSeconds(3600);
        assertThatThrownBy(() -> service.create(1L, request(t, t))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_startInPast_throwsBusinessRule() {
        assertThatThrownBy(() -> service.create(1L, request(NOW.minusSeconds(60), NOW.plusSeconds(3600))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("future");
    }

    @Test
    void create_notOfficer_throwsForbidden() {
        when(guard.requireRole(2L, Role.OFFICER_ADMIN)).thenThrow(new ForbiddenOperationException("no"));

        assertThatThrownBy(() -> service.create(2L, request(NOW.plusSeconds(60), NOW.plusSeconds(120))))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void update_scheduledEvent_changesFields() {
        Event event = event(5, Duration.ofDays(1));
        when(events.findById(5L)).thenReturn(Optional.of(event));
        Instant newStart = NOW.plus(Duration.ofDays(2));

        EventResponse response = service.update(1L, 5L, request(newStart, newStart.plusSeconds(3600)));

        assertThat(response.startsAt()).isEqualTo(newStart);
        assertThat(event.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void update_startedEventSameStart_allowedEvenThoughStartIsPast() {
        Event event = event(5, Duration.ofMinutes(-30));
        when(events.findById(5L)).thenReturn(Optional.of(event));

        EventResponse response = service.update(1L, 5L,
                new EventRequest("Renamed", null, "New room", event.getStartsAt(), event.getEndsAt()));

        assertThat(response.location()).isEqualTo("New room");
    }

    @Test
    void update_moveStartIntoPast_throwsBusinessRule() {
        when(events.findById(5L)).thenReturn(Optional.of(event(5, Duration.ofDays(1))));

        assertThatThrownBy(() -> service.update(1L, 5L, request(NOW.minusSeconds(10), NOW.plusSeconds(3600))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void update_cancelledEvent_throwsBusinessRule() {
        Event event = event(5, Duration.ofDays(1));
        event.cancel(NOW);
        when(events.findById(5L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.update(1L, 5L, request(NOW.plusSeconds(60), NOW.plusSeconds(120))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cancelled");
    }

    @Test
    void update_unknownEvent_throwsNotFound() {
        when(events.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, 5L, request(NOW.plusSeconds(60), NOW.plusSeconds(120))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancel_scheduledEvent_setsCancelled() {
        when(events.findById(5L)).thenReturn(Optional.of(event(5, Duration.ofDays(1))));

        assertThat(service.cancel(1L, 5L).status()).isEqualTo(EventStatus.CANCELLED);
    }

    @Test
    void cancel_alreadyCancelled_throwsBusinessRule() {
        Event event = event(5, Duration.ofDays(1));
        event.cancel(NOW);
        when(events.findById(5L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.cancel(1L, 5L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancel_endedEvent_throwsBusinessRule() {
        when(events.findById(5L)).thenReturn(Optional.of(event(5, Duration.ofDays(-1))));

        assertThatThrownBy(() -> service.cancel(1L, 5L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_page_includesGoingCountAndMyResponse() {
        Event a = event(5, Duration.ofDays(1));
        Event b = event(6, Duration.ofDays(2));
        when(events.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(a, b), PageRequest.of(0, 20), 2));
        when(rsvps.countByEvents(List.of(5L, 6L), RsvpChoice.GOING)).thenReturn(List.of(count(5L, 3)));
        when(rsvps.findByIdEventIdInAndIdUserId(List.of(5L, 6L), 2L))
                .thenReturn(List.of(new Rsvp(6L, 2L, RsvpChoice.NOT_GOING, NOW)));

        PageResponse<EventResponse> page = service.list(2L, null, null, true, null, null);

        assertThat(page.content()).extracting(EventResponse::goingCount).containsExactly(3L, 0L);
        assertThat(page.content()).extracting(EventResponse::myResponse).containsExactly(null, RsvpChoice.NOT_GOING);
        verify(guard).requireActive(2L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_emptyPage_skipsCountQueries() {
        when(events.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(service.list(2L, NOW, NOW.plusSeconds(60), false, 0, 20).content()).isEmpty();
        verify(rsvps, never()).countByEvents(anyList(), any());
    }

    @Test
    void list_fromNotBeforeTo_throwsBusinessRule() {
        assertThatThrownBy(() -> service.list(2L, NOW, NOW, false, null, null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void list_pendingMember_throwsForbidden() {
        when(guard.requireActive(3L)).thenThrow(new ForbiddenOperationException("not active"));

        assertThatThrownBy(() -> service.list(3L, null, null, false, null, null))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void get_event_returnsCountsAndMyResponse() {
        when(events.findById(5L)).thenReturn(Optional.of(event(5, Duration.ofDays(1))));
        when(rsvps.countByIdEventIdAndResponse(5L, RsvpChoice.GOING)).thenReturn(4L);
        when(rsvps.findByIdEventIdInAndIdUserId(eq(List.of(5L)), eq(2L)))
                .thenReturn(List.of(new Rsvp(5L, 2L, RsvpChoice.GOING, NOW)));

        EventResponse response = service.get(2L, 5L);

        assertThat(response.goingCount()).isEqualTo(4);
        assertThat(response.myResponse()).isEqualTo(RsvpChoice.GOING);
    }

    private static RsvpRepository.EventCount count(Long eventId, long total) {
        return new RsvpRepository.EventCount() {
            @Override
            public Long getEventId() {
                return eventId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
