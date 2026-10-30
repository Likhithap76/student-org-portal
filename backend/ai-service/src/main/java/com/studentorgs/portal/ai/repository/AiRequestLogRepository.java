package com.studentorgs.portal.ai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studentorgs.portal.ai.domain.AiRequestLog;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLog, Long> {
}
