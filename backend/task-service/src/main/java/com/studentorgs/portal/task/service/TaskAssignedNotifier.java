package com.studentorgs.portal.task.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.studentorgs.portal.common.client.NotificationClient;
import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.client.NotificationRequest;
import lombok.RequiredArgsConstructor;

/** Tells the assignee about a new task, only after the assignment has committed. Never fails the caller. */
@Component
@RequiredArgsConstructor
public class TaskAssignedNotifier {

    static final String REFERENCE_TABLE = "task";

    private final NotificationClient notificationClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssigned(TaskAssignedEvent event) {
        notificationClient.send(new NotificationRequest(List.of(event.assigneeId()), NotificationKind.TASK_ASSIGNED,
                "You were assigned a task: " + event.title(), REFERENCE_TABLE, event.taskId()));
    }
}
