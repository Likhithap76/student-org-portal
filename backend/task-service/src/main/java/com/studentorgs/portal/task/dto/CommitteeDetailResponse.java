package com.studentorgs.portal.task.dto;

import java.util.List;

import com.studentorgs.portal.task.domain.Committee;
import com.studentorgs.portal.task.domain.CommitteeKind;

public record CommitteeDetailResponse(Long id, String name, CommitteeKind kind, String description,
        List<CommitteeMemberResponse> members) {

    public static CommitteeDetailResponse from(Committee committee, List<CommitteeMemberResponse> members) {
        return new CommitteeDetailResponse(committee.getId(), committee.getName(), committee.getKind(),
                committee.getDescription(), members);
    }
}
