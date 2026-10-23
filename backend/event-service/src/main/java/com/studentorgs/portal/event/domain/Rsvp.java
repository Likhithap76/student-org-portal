package com.studentorgs.portal.event.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A member's RSVP to an event ({@code rsvp}); one row per (event, user), updated in place. */
@Entity
@Table(name = "rsvp")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Rsvp {

    @EmbeddedId
    private RsvpId id;

    @Convert(converter = RsvpChoiceConverter.class)
    @Column(name = "response", nullable = false, length = 20)
    private RsvpChoice response;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Rsvp(Long eventId, Long userId, RsvpChoice response, Instant now) {
        this.id = new RsvpId(eventId, userId);
        this.response = response;
        this.updatedAt = now;
    }

    public void answer(RsvpChoice response, Instant now) {
        this.response = response;
        this.updatedAt = now;
    }
}
