package com.studentorgs.portal.user.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.studentorgs.portal.user.domain.Decision;
import com.studentorgs.portal.user.domain.MembershipApplication;
import jakarta.persistence.LockModeType;

public interface MembershipApplicationRepository extends JpaRepository<MembershipApplication, Long> {

    Optional<MembershipApplication> findFirstByUserIdOrderBySubmittedAtDescIdDesc(Long userId);

    boolean existsByUserIdAndDecision(Long userId, Decision decision);

    Page<MembershipApplication> findByDecision(Decision decision, Pageable pageable);

    /** Locks the row (SELECT ... FOR UPDATE) so two officers cannot decide the same application at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MembershipApplication a where a.id = :id")
    Optional<MembershipApplication> findByIdForUpdate(@Param("id") Long id);
}
