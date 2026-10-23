package com.studentorgs.portal.event.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.studentorgs.portal.event.domain.Rsvp;
import com.studentorgs.portal.event.domain.RsvpChoice;
import com.studentorgs.portal.event.domain.RsvpId;

public interface RsvpRepository extends JpaRepository<Rsvp, RsvpId> {

    interface EventCount {
        Long getEventId();

        long getTotal();
    }

    /** RSVP counts with one response, per event, for a page of events (one GROUP BY query). */
    @Query("""
            select r.id.eventId as eventId, count(r) as total from Rsvp r
            where r.id.eventId in :eventIds and r.response = :response
            group by r.id.eventId
            """)
    List<EventCount> countByEvents(@Param("eventIds") Collection<Long> eventIds, @Param("response") RsvpChoice response);

    long countByIdEventIdAndResponse(Long eventId, RsvpChoice response);

    List<Rsvp> findByIdEventIdInAndIdUserId(Collection<Long> eventIds, Long userId);

    List<Rsvp> findByIdEventIdOrderByUpdatedAtAsc(Long eventId);
}
