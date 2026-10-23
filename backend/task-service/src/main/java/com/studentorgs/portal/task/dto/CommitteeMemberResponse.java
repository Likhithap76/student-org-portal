package com.studentorgs.portal.task.dto;

import java.time.Instant;

import com.studentorgs.portal.task.domain.CommitteeMember;

public record CommitteeMemberResponse(Long userId, String displayName, Instant addedAt) {

    public static CommitteeMemberResponse from(CommitteeMember member, String displayName) {
        return new CommitteeMemberResponse(member.getId().getUserId(), displayName, member.getAddedAt());
    }
}
