package com.studentorgs.portal.user.service;

import com.studentorgs.portal.user.domain.Decision;

/** Published inside the approve/reject transaction; handled after commit. */
public record ApplicationDecidedEvent(Long applicationId, Long applicantId, Decision decision) {
}
