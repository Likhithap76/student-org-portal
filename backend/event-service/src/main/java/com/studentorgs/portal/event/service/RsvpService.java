package com.studentorgs.portal.event.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.event.domain.Event;
import com.studentorgs.portal.event.domain.Rsvp;
import com.studentorgs.portal.event.domain.RsvpId;
import com.studentorgs.portal.event.dto.RsvpEntryResponse;
import com.studentorgs.portal.event.dto.RsvpRequest;
import com.studentorgs.portal.event.repository.RsvpRepository;
import lombok.RequiredArgsConstructor;

/** Member RSVPs: create or change (find-then-save), and the officer list. */
@Service
@RequiredArgsConstructor
public class RsvpService {

    private final EventService eventService;
    private final RsvpRepository rsvps;
    private final MemberGuard guard;
    private final UserDirectoryClient users;
    private final Clock clock;

    @Transactional
    public RsvpEntryResponse respond(Long userId, Long eventId, RsvpRequest request) {
        guard.requireActive(userId);
        Event event = eventService.find(eventId);
        Instant now = clock.instant();
        if (event.isCancelled()) {
            throw new BusinessRuleException("You cannot RSVP to a cancelled event");
        }
        if (event.hasStarted(now)) {
            throw new BusinessRuleException("You cannot RSVP after the event has started");
        }
        Rsvp rsvp = rsvps.findById(new RsvpId(eventId, userId)).orElse(null);
        if (rsvp == null) {
            rsvp = new Rsvp(eventId, userId, request.response(), now);
        } else {
            rsvp.answer(request.response(), now);
        }
        return RsvpEntryResponse.from(rsvps.save(rsvp), null);
    }

    @Transactional(readOnly = true)
    public List<RsvpEntryResponse> list(Long officerId, Long eventId) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        eventService.find(eventId);
        List<Rsvp> all = rsvps.findByIdEventIdOrderByUpdatedAtAsc(eventId);
        Map<Long, String> names = users.names(all.stream().map(r -> r.getId().getUserId()).toList());
        return all.stream().map(r -> RsvpEntryResponse.from(r, names.get(r.getId().getUserId()))).toList();
    }
}
