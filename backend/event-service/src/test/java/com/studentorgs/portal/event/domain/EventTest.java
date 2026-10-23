package com.studentorgs.portal.event.domain;

import static com.studentorgs.portal.event.TestEvents.NOW;
import static com.studentorgs.portal.event.TestEvents.event;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class EventTest {

    @Test
    void hasStarted_atStartInstant_isTrue() {
        Event event = event(1, Duration.ZERO);

        assertThat(event.hasStarted(NOW)).isTrue();
        assertThat(event.hasStarted(NOW.minusSeconds(1))).isFalse();
    }

    @Test
    void hasEnded_atEndInstant_isTrue() {
        Event event = event(1, Duration.ZERO);

        assertThat(event.hasEnded(NOW.plus(Duration.ofHours(2)))).isTrue();
        assertThat(event.hasEnded(NOW.plusSeconds(60))).isFalse();
    }

    @Test
    void cancel_scheduled_setsCancelledAndUpdatedAt() {
        Event event = event(1, Duration.ofDays(1));

        event.cancel(NOW);

        assertThat(event.isCancelled()).isTrue();
        assertThat(event.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void record_existingAttendance_overwritesStatusAndRecorder() {
        Attendance row = new Attendance(1L, 2L, AttendanceStatus.ABSENT, 9L, NOW);

        row.record(AttendanceStatus.PRESENT, 8L, NOW.plusSeconds(5));

        assertThat(row.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(row.getRecordedBy()).isEqualTo(8L);
        assertThat(row.getRecordedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(row.getId()).isEqualTo(new AttendanceId(1L, 2L));
    }
}
