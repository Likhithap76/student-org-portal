package com.studentorgs.portal.ai.domain;

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

/**
 * One row per summary request ({@code ai_request_log}). By design it holds no meeting text and no AI
 * output: only who, when, how long the input was, the outcome and the latency.
 */
@Entity
@Table(name = "ai_request_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "input_chars", nullable = false)
    private int inputChars;

    @Convert(converter = AiOutcomeConverter.class)
    @Column(name = "outcome", nullable = false, length = 30)
    private AiOutcome outcome;

    /** Supabase column is int4 and nullable. */
    @Column(name = "latency_ms")
    private Integer latencyMs;

    public AiRequestLog(Long userId, Instant requestedAt, int inputChars, AiOutcome outcome, Integer latencyMs) {
        this.userId = userId;
        this.requestedAt = requestedAt;
        this.inputChars = inputChars;
        this.outcome = outcome;
        this.latencyMs = latencyMs;
    }
}
