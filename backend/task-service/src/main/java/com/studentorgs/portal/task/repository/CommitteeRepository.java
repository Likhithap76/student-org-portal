package com.studentorgs.portal.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.studentorgs.portal.task.domain.Committee;

public interface CommitteeRepository extends JpaRepository<Committee, Long> {

    boolean existsByNameIgnoreCase(String name);
}
