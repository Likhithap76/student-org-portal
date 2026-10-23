package com.studentorgs.portal.event.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageRequests;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.event.domain.Event;
import com.studentorgs.portal.event.domain.Rsvp;
import com.studentorgs.portal.event.domain.RsvpChoice;
import com.studentorgs.portal.event.dto.EventRequest;
import com.studentorgs.portal.event.dto.EventResponse;
import com.studentorgs.portal.event.repository.EventRepository;
import com.studentorgs.portal.event.repository.RsvpRepository;
import lombok.RequiredArgsConstructor;

/** Scheduling, editing, cancelling and listing events. */
@Service
@RequiredArgsConstructor
public class EventService {

    private static final Sort BY_START = Sort.by("startsAt").ascending().and(Sort.by("id").ascending());

    private final EventRepository events;
    private final RsvpRepository rsvps;
    private final MemberGuard guard;
    private final Clock clock;

    @Transactional
    public EventResponse create(Long officerId, EventRequest request) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        Instant now = clock.instant();
        requireEndAfterStart(request);
        requireFuture(request.startsAt(), now);
        Event event = events.save(Event.schedule(request.title().trim(), trimToNull(request.description()),
                trimToNull(request.location()), request.startsAt(), request.endsAt(), officerId, now));
        return EventResponse.from(event, 0, null);
    }

    @Transactional
    public EventResponse update(Long officerId, Long eventId, EventRequest request) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        Event event = find(eventId);
        if (event.isCancelled()) {
            throw new BusinessRuleException("A cancelled event cannot be edited");
        }
        Instant now = clock.instant();
        requireEndAfterStart(request);
        if (!request.startsAt().equals(event.getStartsAt())) {
            requireFuture(request.startsAt(), now);
        }
        event.update(request.title().trim(), trimToNull(request.description()), trimToNull(request.location()),
                request.startsAt(), request.endsAt(), now);
        return withCounts(event, officerId);
    }

    @Transactional
    public EventResponse cancel(Long officerId, Long eventId) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        Event event = find(eventId);
        Instant now = clock.instant();
        if (event.isCancelled()) {
            throw new BusinessRuleException("The event is already cancelled");
        }
        if (event.hasEnded(now)) {
            throw new BusinessRuleException("An event that has ended cannot be cancelled");
        }
        event.cancel(now);
        return withCounts(event, officerId);
    }

    /** Paged by start time. {@code upcoming=true} keeps only events that have not started yet. */
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> list(Long userId, Instant from, Instant to, boolean upcoming,
            Integer page, Integer size) {
        guard.requireActive(userId);
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BusinessRuleException("'from' must be before 'to'");
        }
        Instant effectiveFrom = from;
        if (upcoming) {
            Instant now = clock.instant();
            effectiveFrom = from == null || from.isBefore(now) ? now : from;
        }
        Specification<Event> filter = Specification.where(EventRepository.startsFrom(effectiveFrom))
                .and(EventRepository.startsBefore(to));
        Page<Event> result = events.findAll(filter, PageRequests.of(page, size, BY_START));

        List<Long> ids = result.getContent().stream().map(Event::getId).toList();
        Map<Long, Long> going = ids.isEmpty() ? Map.of() : rsvps.countByEvents(ids, RsvpChoice.GOING).stream()
                .collect(Collectors.toMap(RsvpRepository.EventCount::getEventId, RsvpRepository.EventCount::getTotal));
        Map<Long, RsvpChoice> mine = ids.isEmpty() ? Map.of() : rsvps.findByIdEventIdInAndIdUserId(ids, userId).stream()
                .collect(Collectors.toMap(r -> r.getId().getEventId(), Rsvp::getResponse));
        return PageResponse.from(result,
                e -> EventResponse.from(e, going.getOrDefault(e.getId(), 0L), mine.get(e.getId())));
    }

    @Transactional(readOnly = true)
    public EventResponse get(Long userId, Long eventId) {
        guard.requireActive(userId);
        return withCounts(find(eventId), userId);
    }

    Event find(Long eventId) {
        return events.findById(eventId).orElseThrow(() -> new NotFoundException("Event " + eventId + " not found"));
    }

    private EventResponse withCounts(Event event, Long userId) {
        long going = rsvps.countByIdEventIdAndResponse(event.getId(), RsvpChoice.GOING);
        RsvpChoice mine = rsvps.findByIdEventIdInAndIdUserId(List.of(event.getId()), userId).stream()
                .findFirst().map(Rsvp::getResponse).orElse(null);
        return EventResponse.from(event, going, mine);
    }

    private static void requireEndAfterStart(EventRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessRuleException("endsAt must be after startsAt");
        }
    }

    private static void requireFuture(Instant startsAt, Instant now) {
        if (!startsAt.isAfter(now)) {
            throw new BusinessRuleException("startsAt must be in the future");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
