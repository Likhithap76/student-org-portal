package com.studentorgs.portal.common.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import com.studentorgs.portal.common.audit.AuditEvent;
import com.studentorgs.portal.common.audit.AuditWriter;
import jakarta.persistence.EntityManagerFactory;

/**
 * Registers the {@code audit_event} entity package for entity scanning and provides
 * {@link AuditWriter} to every JPA service.
 */
@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)
@AutoConfigurationPackage(basePackageClasses = AuditEvent.class)
@ConditionalOnClass(EntityManagerFactory.class)
public class AuditAutoConfiguration {

    @Bean
    AuditWriter auditWriter(EntityManagerFactory entityManagerFactory, Clock clock) {
        return new AuditWriter(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory), clock);
    }
}
