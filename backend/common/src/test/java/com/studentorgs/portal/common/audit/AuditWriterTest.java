package com.studentorgs.portal.common.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
@Import(AuditWriterTest.Config.class)
class AuditWriterTest {

    static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @TestConfiguration
    static class Config {
        @Bean
        AuditWriter auditWriter(EntityManager entityManager) {
            return new AuditWriter(entityManager, Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }

    @Autowired
    private AuditWriter auditWriter;

    @Autowired
    private TestEntityManager em;

    @Test
    void record_insideTransaction_persistsRow() {
        AuditEvent saved = auditWriter.record(1L, "membership.approve", "membership_application", 10L);
        em.flush();
        em.clear();

        AuditEvent found = em.find(AuditEvent.class, saved.getId());
        assertThat(found.getActorId()).isEqualTo(1L);
        assertThat(found.getAction()).isEqualTo("membership.approve");
        assertThat(found.getTargetTable()).isEqualTo("membership_application");
        assertThat(found.getTargetId()).isEqualTo(10L);
        assertThat(found.getOccurredAt()).isEqualTo(NOW);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void record_withoutTransaction_throws() {
        assertThatThrownBy(() -> auditWriter.record(1L, "user.deactivate", "app_user", 2L))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
