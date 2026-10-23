package com.studentorgs.portal.event.repository;

import java.time.Instant;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.studentorgs.portal.event.domain.Event;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    /** Events starting at or after {@code from} (inclusive); no filter when null. */
    static Specification<Event> startsFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("startsAt"), from);
    }

    /** Events starting before {@code to} (exclusive); no filter when null. */
    static Specification<Event> startsBefore(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThan(root.get("startsAt"), to);
    }
}
