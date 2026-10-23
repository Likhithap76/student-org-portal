package com.studentorgs.portal.event.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.studentorgs.portal.event.domain.Rsvp;
import com.studentorgs.portal.event.domain.RsvpChoice;

/** One RSVP. {@code displayName} is filled in for officer lists. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RsvpEntryResponse(Long eventId, Long userId, String displayName, RsvpChoice response, Instant updatedAt) {

    public static RsvpEntryResponse from(Rsvp rsvp, String displayName) {
        return new RsvpEntryResponse(rsvp.getId().getEventId(), rsvp.getId().getUserId(), displayName,
                rsvp.getResponse(), rsvp.getUpdatedAt());
    }
}
