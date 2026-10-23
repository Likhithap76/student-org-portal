package com.studentorgs.portal.task.service;

import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.studentorgs.portal.common.client.NotificationClient;
import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.client.NotificationRequest;

@ExtendWith(MockitoExtension.class)
class TaskAssignedNotifierTest {

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private TaskAssignedNotifier notifier;

    @Test
    void onAssigned_sendsTaskAssignedToAssignee() {
        notifier.onAssigned(new TaskAssignedEvent(40L, 2L, "Book room"));

        verify(notificationClient).send(new NotificationRequest(List.of(2L), NotificationKind.TASK_ASSIGNED,
                "You were assigned a task: Book room", "task", 40L));
    }
}
