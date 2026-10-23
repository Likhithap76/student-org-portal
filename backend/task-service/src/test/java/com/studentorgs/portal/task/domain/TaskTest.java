package com.studentorgs.portal.task.domain;

import static com.studentorgs.portal.task.TestTasks.NOW;
import static com.studentorgs.portal.task.TestTasks.TODAY;
import static com.studentorgs.portal.task.TestTasks.task;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.studentorgs.portal.common.error.BusinessRuleException;

class TaskTest {

    private static Task inStatus(TaskStatus status) {
        Task task = task(1, 1, 2, null);
        if (status != TaskStatus.TODO) {
            task.changeStatus(TaskStatus.IN_PROGRESS, NOW);
        }
        if (status == TaskStatus.DONE) {
            task.changeStatus(TaskStatus.DONE, NOW);
        }
        return task;
    }

    @Test
    void assign_newTask_isTodoWithoutCompletedAt() {
        Task task = task(1, 1, 2, TODAY);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(task.getCompletedAt()).isNull();
    }

    @Test
    void changeStatus_toDone_setsCompletedAt() {
        Task task = inStatus(TaskStatus.IN_PROGRESS);

        task.changeStatus(TaskStatus.DONE, NOW.plusSeconds(60));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(task.getCompletedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(task.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void changeStatus_reopen_clearsCompletedAt() {
        Task task = inStatus(TaskStatus.DONE);

        task.changeStatus(TaskStatus.IN_PROGRESS, NOW.plusSeconds(120));

        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getCompletedAt()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"TODO,TODO", "TODO,DONE", "IN_PROGRESS,TODO", "IN_PROGRESS,IN_PROGRESS", "DONE,TODO", "DONE,DONE"})
    void changeStatus_illegalMove_throwsBusinessRuleAndKeepsState(TaskStatus from, TaskStatus to) {
        Task task = inStatus(from);
        var completedAt = task.getCompletedAt();

        assertThatThrownBy(() -> task.changeStatus(to, NOW.plusSeconds(5))).isInstanceOf(BusinessRuleException.class);
        assertThat(task.getStatus()).isEqualTo(from);
        assertThat(task.getCompletedAt()).isEqualTo(completedAt);
    }

    @Test
    void isOverdue_openPastDue_true() {
        assertThat(task(1, 1, 2, TODAY.minusDays(1)).isOverdue(TODAY)).isTrue();
    }

    @Test
    void isOverdue_dueTodayOrNoDateOrDone_false() {
        assertThat(task(1, 1, 2, TODAY).isOverdue(TODAY)).isFalse();
        assertThat(task(1, 1, 2, null).isOverdue(TODAY)).isFalse();
        Task done = inStatus(TaskStatus.DONE);
        done.edit("t", 2L, TODAY.minusDays(3), NOW);
        assertThat(done.isOverdue(TODAY)).isFalse();
    }
}
