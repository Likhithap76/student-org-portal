package com.studentorgs.portal.task.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Membership of a user in a committee ({@code committee_member}). */
@Entity
@Table(name = "committee_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommitteeMember {

    @EmbeddedId
    private CommitteeMemberId id;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    public CommitteeMember(Long committeeId, Long userId, Instant addedAt) {
        this.id = new CommitteeMemberId(committeeId, userId);
        this.addedAt = addedAt;
    }
}
