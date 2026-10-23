package com.studentorgs.portal.task.dto;

import com.studentorgs.portal.task.domain.Committee;
import com.studentorgs.portal.task.domain.CommitteeKind;

public record CommitteeSummaryResponse(Long id, String name, CommitteeKind kind, String description, long memberCount) {

    public static CommitteeSummaryResponse from(Committee committee, long memberCount) {
        return new CommitteeSummaryResponse(committee.getId(), committee.getName(), committee.getKind(),
                committee.getDescription(), memberCount);
    }
}
