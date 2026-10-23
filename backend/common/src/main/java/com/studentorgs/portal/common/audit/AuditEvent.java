package com.studentorgs.portal.common.audit;

import java.time.Instant;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Append-only row in {@code audit_event}. Any service may insert; nothing updates or deletes. */
@Entity
@Immutable
@Table(name = "audit_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "target_table", nullable = false, length = 50)
    private String targetTable;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public AuditEvent(Long actorId, String action, String targetTable, Long targetId, Instant occurredAt) {
        this.actorId = actorId;
        this.action = action;
        this.targetTable = targetTable;
        this.targetId = targetId;
        this.occurredAt = occurredAt;
    }
}
