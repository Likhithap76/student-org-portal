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

/** Attendance recorded by an officer ({@code attendance}), independent of RSVP. Never deleted. */
@Entity
@Table(name = "attendance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attendance {

    @EmbeddedId
    private AttendanceId id;

    @Convert(converter = AttendanceStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceStatus status;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public Attendance(Long eventId, Long userId, AttendanceStatus status, Long recordedBy, Instant now) {
        this.id = new AttendanceId(eventId, userId);
        record(status, recordedBy, now);
    }

    public void record(AttendanceStatus status, Long recordedBy, Instant now) {
        this.status = status;
        this.recordedBy = recordedBy;
        this.recordedAt = now;
    }
}
