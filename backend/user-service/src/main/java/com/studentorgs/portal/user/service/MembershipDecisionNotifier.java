package com.studentorgs.portal.user.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.studentorgs.portal.common.client.NotificationClient;
import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.client.NotificationRequest;
import com.studentorgs.portal.user.domain.Decision;
import lombok.RequiredArgsConstructor;

/**
 * Tells the applicant about the decision, only after the decision has committed.
 * NotificationClient never throws, so a notification failure cannot undo the decision.
 */
@Component
@RequiredArgsConstructor
public class MembershipDecisionNotifier {

    static final String REFERENCE_TABLE = "membership_application";

    private final NotificationClient notificationClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecision(ApplicationDecidedEvent event) {
        String message = event.decision() == Decision.APPROVED
                ? "Your membership application was approved. Welcome!"
                : "Your membership application was not approved.";
        notificationClient.send(new NotificationRequest(List.of(event.applicantId()),
                NotificationKind.APPLICATION_DECISION, message, REFERENCE_TABLE, event.applicationId()));
    }
}
