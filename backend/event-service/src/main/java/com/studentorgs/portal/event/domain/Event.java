package com.studentorgs.portal.event.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An organization event ({@code event}). Never deleted; cancelling sets status to cancelled. */
@Entity
@Table(name = "event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Convert(converter = EventStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Event schedule(String title, String description, String location, Instant startsAt, Instant endsAt,
            Long createdBy, Instant now) {
        Event event = new Event();
        event.title = title;
        event.description = description;
        event.location = location;
        event.startsAt = startsAt;
        event.endsAt = endsAt;
        event.status = EventStatus.SCHEDULED;
        event.createdBy = createdBy;
        event.createdAt = now;
        event.updatedAt = now;
        return event;
    }

    public void update(String title, String description, String location, Instant startsAt, Instant endsAt, Instant now) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.status = EventStatus.CANCELLED;
        this.updatedAt = now;
    }

    public boolean isCancelled() {
        return status == EventStatus.CANCELLED;
    }

    public boolean hasStarted(Instant now) {
        return !now.isBefore(startsAt);
    }

    public boolean hasEnded(Instant now) {
        return !now.isBefore(endsAt);
    }
}
