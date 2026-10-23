package com.studentorgs.portal.task.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

import com.studentorgs.portal.common.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A task in a committee ({@code task}). Never deleted.
 * Invariant: {@code status = done} ⇔ {@code completed_at} is set.
 */
@Entity
@Table(name = "task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "committee_id", nullable = false)
    private Long committeeId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "assignee_id", nullable = false)
    private Long assigneeId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Convert(converter = TaskStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Task assign(Long committeeId, String title, Long assigneeId, LocalDate dueDate, Long createdBy,
            Instant now) {
        Task task = new Task();
        task.committeeId = committeeId;
        task.title = title;
        task.assigneeId = assigneeId;
        task.dueDate = dueDate;
        task.status = TaskStatus.TODO;
        task.createdBy = createdBy;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    public void edit(String title, Long assigneeId, LocalDate dueDate, Instant now) {
        this.title = title;
        this.assigneeId = assigneeId;
        this.dueDate = dueDate;
        this.updatedAt = now;
    }

    /**
     * Allowed: todo → in_progress → done, and done → in_progress (reopen).
     * Done sets {@code completed_at}; reopening clears it. Anything else is a business-rule error.
     */
    public void changeStatus(TaskStatus next, Instant now) {
        boolean allowed = (status == TaskStatus.TODO && next == TaskStatus.IN_PROGRESS)
                || (status == TaskStatus.IN_PROGRESS && next == TaskStatus.DONE)
                || (status == TaskStatus.DONE && next == TaskStatus.IN_PROGRESS);
        if (!allowed) {
            throw new BusinessRuleException("A task cannot move from " + label(status) + " to " + label(next));
        }
        this.status = next;
        this.completedAt = next == TaskStatus.DONE ? now : null;
        this.updatedAt = now;
    }

    public boolean isOpen() {
        return status != TaskStatus.DONE;
    }

    public boolean isOverdue(LocalDate today) {
        return isOpen() && dueDate != null && dueDate.isBefore(today);
    }

    private static String label(TaskStatus status) {
        return status.name().toLowerCase(Locale.ROOT);
    }
}
