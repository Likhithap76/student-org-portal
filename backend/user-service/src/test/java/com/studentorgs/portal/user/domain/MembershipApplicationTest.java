package com.studentorgs.portal.user.domain;

import static com.studentorgs.portal.user.TestUsers.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.studentorgs.portal.common.error.BusinessRuleException;

class MembershipApplicationTest {

    @Test
    void submit_newApplication_isPendingWithoutDecider() {
        MembershipApplication application = MembershipApplication.submit(5L, NOW);

        assertThat(application.isPending()).isTrue();
        assertThat(application.getDecidedBy()).isNull();
        assertThat(application.getDecidedAt()).isNull();
    }

    @Test
    void approve_pending_setsDecisionAndDecider() {
        MembershipApplication application = MembershipApplication.submit(5L, NOW);

        application.approve(1L, NOW.plusSeconds(60));

        assertThat(application.getDecision()).isEqualTo(Decision.APPROVED);
        assertThat(application.getDecidedBy()).isEqualTo(1L);
        assertThat(application.getDecidedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void reject_pending_setsDecisionAndDecider() {
        MembershipApplication application = MembershipApplication.submit(5L, NOW);

        application.reject(2L, NOW);

        assertThat(application.getDecision()).isEqualTo(Decision.REJECTED);
        assertThat(application.getDecidedBy()).isEqualTo(2L);
        assertThat(application.getDecidedAt()).isEqualTo(NOW);
    }

    @Test
    void approve_alreadyDecided_throwsBusinessRule() {
        MembershipApplication application = MembershipApplication.submit(5L, NOW);
        application.reject(2L, NOW);

        assertThatThrownBy(() -> application.approve(1L, NOW)).isInstanceOf(BusinessRuleException.class);
        assertThat(application.getDecision()).isEqualTo(Decision.REJECTED);
        assertThat(application.getDecidedBy()).isEqualTo(2L);
    }
}
