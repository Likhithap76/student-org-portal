package com.studentorgs.portal.task.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Primary key of {@code committee_member}: (committee_id, user_id). */
@Embeddable
@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommitteeMemberId implements Serializable {

    @Column(name = "committee_id", nullable = false)
    private Long committeeId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
