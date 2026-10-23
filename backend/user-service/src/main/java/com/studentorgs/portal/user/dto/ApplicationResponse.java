package com.studentorgs.portal.user.dto;

import java.time.Instant;

import com.studentorgs.portal.user.domain.AppUser;
import com.studentorgs.portal.user.domain.Decision;
import com.studentorgs.portal.user.domain.MembershipApplication;

/** A membership application with the applicant's name and email (both owned by user-service). */
public record ApplicationResponse(
        Long id,
        Long userId,
        String applicantName,
        String applicantEmail,
        Instant submittedAt,
        Decision decision,
        Long decidedBy,
        Instant decidedAt) {

    public static ApplicationResponse from(MembershipApplication application, AppUser applicant) {
        return new ApplicationResponse(
                application.getId(),
                application.getUserId(),
                applicant == null ? null : applicant.getDisplayName(),
                applicant == null ? null : applicant.getEmail(),
                application.getSubmittedAt(),
                application.getDecision(),
                application.getDecidedBy(),
                application.getDecidedAt());
    }
}
