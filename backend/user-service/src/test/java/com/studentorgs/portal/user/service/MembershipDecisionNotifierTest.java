package com.studentorgs.portal.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.studentorgs.portal.common.client.NotificationClient;
import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.client.NotificationRequest;
import com.studentorgs.portal.user.domain.Decision;

@ExtendWith(MockitoExtension.class)
class MembershipDecisionNotifierTest {

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private MembershipDecisionNotifier notifier;

    @Test
    void onDecision_approved_notifiesApplicant() {
        notifier.onDecision(new ApplicationDecidedEvent(50L, 5L, Decision.APPROVED));

        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient).send(sent.capture());
        assertThat(sent.getValue().recipientIds()).isEqualTo(List.of(5L));
        assertThat(sent.getValue().kind()).isEqualTo(NotificationKind.APPLICATION_DECISION);
        assertThat(sent.getValue().referenceTable()).isEqualTo("membership_application");
        assertThat(sent.getValue().referenceId()).isEqualTo(50L);
        assertThat(sent.getValue().message()).contains("approved");
    }

    @Test
    void onDecision_rejected_sendsNotApprovedMessage() {
        notifier.onDecision(new ApplicationDecidedEvent(50L, 5L, Decision.REJECTED));

        ArgumentCaptor<NotificationRequest> sent = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient).send(sent.capture());
        assertThat(sent.getValue().message()).contains("not approved");
    }
}
