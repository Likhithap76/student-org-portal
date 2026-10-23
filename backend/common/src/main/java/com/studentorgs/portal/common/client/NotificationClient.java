package com.studentorgs.portal.common.client;

import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends notifications through notification-service. Best effort: a failure is logged as a
 * warning and never fails the caller's main action.
 */
@Slf4j
public class NotificationClient {

    static final String PATH = "/internal/notifications";

    private final RestClient restClient;

    public NotificationClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** @return true if notification-service accepted the request */
    public boolean send(NotificationRequest request) {
        if (request.recipientIds() == null || request.recipientIds().isEmpty()) {
            return true;
        }
        try {
            restClient.post().uri(PATH).body(request).retrieve().toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not send {} notification to {} recipient(s): {}",
                    request.kind(), request.recipientIds().size(), e.getClass().getSimpleName());
            return false;
        }
    }
}
