package com.studentorgs.portal.event.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studentorgs.portal.event.domain.Attendance;
import com.studentorgs.portal.event.domain.AttendanceId;

public interface AttendanceRepository extends JpaRepository<Attendance, AttendanceId> {

    List<Attendance> findByIdEventId(Long eventId);

    List<Attendance> findByIdEventIdAndIdUserIdIn(Long eventId, Collection<Long> userIds);
}
