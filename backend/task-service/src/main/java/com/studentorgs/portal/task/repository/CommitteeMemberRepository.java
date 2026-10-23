package com.studentorgs.portal.task.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.studentorgs.portal.task.domain.CommitteeMember;
import com.studentorgs.portal.task.domain.CommitteeMemberId;

public interface CommitteeMemberRepository extends JpaRepository<CommitteeMember, CommitteeMemberId> {

    interface CommitteeCount {
        Long getCommitteeId();

        long getTotal();
    }

    List<CommitteeMember> findByIdCommitteeIdOrderByAddedAtAscIdUserIdAsc(Long committeeId);

    List<CommitteeMember> findByIdCommitteeIdAndIdUserIdIn(Long committeeId, Collection<Long> userIds);

    /** Member counts for a page of committees (one GROUP BY query). */
    @Query("""
            select m.id.committeeId as committeeId, count(m) as total from CommitteeMember m
            where m.id.committeeId in :committeeIds
            group by m.id.committeeId
            """)
    List<CommitteeCount> countByCommittees(@Param("committeeIds") Collection<Long> committeeIds);
}
