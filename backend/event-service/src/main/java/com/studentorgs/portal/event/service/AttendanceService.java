package com.studentorgs.portal.event.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.event.domain.Attendance;
import com.studentorgs.portal.event.domain.AttendanceStatus;
import com.studentorgs.portal.event.domain.Event;
import com.studentorgs.portal.event.domain.RsvpChoice;
import com.studentorgs.portal.event.dto.AttendanceEntryRequest;
import com.studentorgs.portal.event.dto.AttendanceEntryResponse;
import com.studentorgs.portal.event.dto.AttendanceResponse;
import com.studentorgs.portal.event.repository.AttendanceRepository;
import com.studentorgs.portal.event.repository.RsvpRepository;
import lombok.RequiredArgsConstructor;

/** Officer-recorded attendance (independent of RSVP) and the attendance rate. */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final EventService eventService;
    private final AttendanceRepository attendance;
    private final RsvpRepository rsvps;
    private final MemberGuard guard;
    private final UserDirectoryClient users;
    private final Clock clock;

    /** Create or update rows (find-then-save); recorded_by = the officer. Only once the event has started. */
    @Transactional
    public AttendanceResponse record(Long officerId, Long eventId, List<AttendanceEntryRequest> entries) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        Event event = eventService.find(eventId);
        Instant now = clock.instant();
        if (event.isCancelled()) {
            throw new BusinessRuleException("Attendance cannot be recorded for a cancelled event");
        }
        if (!event.hasStarted(now)) {
            throw new BusinessRuleException("Attendance can only be recorded after the event starts");
        }
        List<Long> userIds = entries.stream().map(AttendanceEntryRequest::userId).toList();
        Set<Long> unique = new HashSet<>(userIds);
        if (unique.size() != userIds.size()) {
            throw new BusinessRuleException("Each user may appear only once");
        }
        Map<Long, String> known = users.names(unique);
        List<Long> unknown = userIds.stream().filter(id -> !known.containsKey(id)).toList();
        if (!unknown.isEmpty()) {
            throw new BusinessRuleException("Unknown user ids: " + unknown);
        }

        Map<Long, Attendance> existing = attendance.findByIdEventIdAndIdUserIdIn(eventId, unique).stream()
                .collect(Collectors.toMap(a -> a.getId().getUserId(), Function.identity()));
        for (AttendanceEntryRequest entry : entries) {
            Attendance row = existing.get(entry.userId());
            if (row == null) {
                attendance.save(new Attendance(eventId, entry.userId(), entry.status(), officerId, now));
            } else {
                row.record(entry.status(), officerId, now);
            }
        }
        attendance.flush();
        return summarize(eventId);
    }

    @Transactional(readOnly = true)
    public AttendanceResponse summary(Long officerId, Long eventId) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        eventService.find(eventId);
        return summarize(eventId);
    }

    private AttendanceResponse summarize(Long eventId) {
        List<Attendance> rows = attendance.findByIdEventId(eventId);
        Map<Long, String> names = users.names(rows.stream().map(a -> a.getId().getUserId()).toList());
        List<AttendanceEntryResponse> entries = rows.stream()
                .map(a -> AttendanceEntryResponse.from(a, names.get(a.getId().getUserId())))
                .sorted(Comparator.comparing(AttendanceEntryResponse::displayName, Comparator.nullsLast(String::compareTo))
                        .thenComparing(AttendanceEntryResponse::userId))
                .toList();
        long present = rows.stream().filter(a -> a.getStatus() == AttendanceStatus.PRESENT).count();
        long going = rsvps.countByIdEventIdAndResponse(eventId, RsvpChoice.GOING);
        return new AttendanceResponse(eventId, entries, present, going, rate(present, going));
    }

    static Double rate(long present, long going) {
        if (going == 0) {
            return null;
        }
        return Math.round(present * 10000.0 / going) / 10000.0;
    }
}
