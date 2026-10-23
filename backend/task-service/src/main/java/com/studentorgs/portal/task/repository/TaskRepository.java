package com.studentorgs.portal.task.repository;

import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.studentorgs.portal.task.domain.Task;
import com.studentorgs.portal.task.domain.TaskStatus;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    boolean existsByCommitteeIdAndAssigneeIdAndStatusNot(Long committeeId, Long assigneeId, TaskStatus status);

    List<Task> findByAssigneeId(Long assigneeId);

    /** Equality filter on {@code attribute}; no filter when {@code value} is null. */
    static Specification<Task> hasValue(String attribute, Object value) {
        return (root, query, cb) -> value == null ? null : cb.equal(root.get(attribute), value);
    }
}
