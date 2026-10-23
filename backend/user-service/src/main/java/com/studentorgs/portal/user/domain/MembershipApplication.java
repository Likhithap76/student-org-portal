package com.studentorgs.portal.user.domain;

import java.time.Instant;

import com.studentorgs.portal.common.error.BusinessRuleException;
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
 * A membership application ({@code membership_application}).
 * {@code decided_by} / {@code decided_at} are set exactly when the decision is not pending.
 */
@Entity
@Table(name = "membership_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembershipApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Convert(converter = DecisionConverter.class)
    @Column(name = "decision", nullable = false, length = 20)
    private Decision decision;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    public static MembershipApplication submit(Long userId, Instant now) {
        MembershipApplication application = new MembershipApplication();
        application.userId = userId;
        application.submittedAt = now;
        application.decision = Decision.PENDING;
        return application;
    }

    public boolean isPending() {
        return decision == Decision.PENDING;
    }

    public void approve(Long officerId, Instant now) {
        decide(Decision.APPROVED, officerId, now);
    }

    public void reject(Long officerId, Instant now) {
        decide(Decision.REJECTED, officerId, now);
    }

    private void decide(Decision outcome, Long officerId, Instant now) {
        if (!isPending()) {
            throw new BusinessRuleException("Application " + id + " has already been " + decision.name().toLowerCase());
        }
        this.decision = outcome;
        this.decidedBy = officerId;
        this.decidedAt = now;
    }
}
