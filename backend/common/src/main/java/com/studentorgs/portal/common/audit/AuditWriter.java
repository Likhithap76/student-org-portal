package com.studentorgs.portal.common.audit;

import java.time.Clock;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Writes an {@code audit_event} row inside the caller's transaction, so the audit row and the
 * change it describes commit or roll back together. Calling it outside a transaction fails.
 */
@RequiredArgsConstructor
public class AuditWriter {

    private final EntityManager entityManager;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public AuditEvent record(Long actorId, String action, String targetTable, Long targetId) {
        AuditEvent event = new AuditEvent(actorId, action, targetTable, targetId, clock.instant());
        entityManager.persist(event);
        return event;
    }
}
