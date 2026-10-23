package com.studentorgs.portal.event.dto;

import java.time.Instant;

import com.studentorgs.portal.event.domain.Event;
import com.studentorgs.portal.event.domain.EventStatus;
import com.studentorgs.portal.event.domain.RsvpChoice;

/** An event with its going count and the caller's own RSVP (null if none). */
public record EventResponse(
        Long id,
        String title,
        String description,
        String location,
        Instant startsAt,
        Instant endsAt,
        EventStatus status,
        Long createdBy,
        Instant createdAt,
        Instant updatedAt,
        long goingCount,
        RsvpChoice myResponse) {

    public static EventResponse from(Event event, long goingCount, RsvpChoice myResponse) {
        return new EventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getLocation(),
                event.getStartsAt(), event.getEndsAt(), event.getStatus(), event.getCreatedBy(),
                event.getCreatedAt(), event.getUpdatedAt(), goingCount, myResponse);
    }
}
