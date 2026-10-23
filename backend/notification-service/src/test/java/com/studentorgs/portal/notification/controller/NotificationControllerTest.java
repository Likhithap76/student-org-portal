package com.studentorgs.portal.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.AuthenticatedUser;
import com.studentorgs.portal.common.security.InternalTokenFilter;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.notification.dto.NotificationResponse;
import com.studentorgs.portal.notification.service.NotificationService;

@WebMvcTest({NotificationController.class, InternalNotificationController.class})
@ActiveProfiles("test")
class NotificationControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final NotificationResponse ITEM = new NotificationResponse(1L, NotificationKind.APPLICATION_DECISION,
            "Your membership application was approved.", "membership_application", 9L, NOW, null, false);
    private static final String CREATE_BODY = """
            {"recipientIds":[2,3],"kind":"task_assigned","message":"New task","referenceTable":"task","referenceId":40}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private NotificationService notificationService;

    private String pendingUser() {
        return "Bearer " + jwtService.issue(new AuthenticatedUser(2L, "a@uta.edu", "A", Role.MEMBER, UserStatus.PENDING));
    }

    @Test
    void mine_pendingUser_returnsPageWithLowercaseKind() throws Exception {
        when(notificationService.mine(2L, true, null, null)).thenReturn(new PageResponse<>(List.of(ITEM), 0, 20, 1, 1, true));

        mockMvc.perform(get("/notifications/me").param("unreadOnly", "true").header("Authorization", pendingUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].kind").value("application_decision"))
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    void mine_noToken_returns401() throws Exception {
        mockMvc.perform(get("/notifications/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void mine_inactiveAccount_returns403() throws Exception {
        when(notificationService.mine(2L, false, null, null)).thenThrow(new ForbiddenOperationException("inactive"));

        mockMvc.perform(get("/notifications/me").header("Authorization", pendingUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unreadCount_returnsCount() throws Exception {
        when(notificationService.unreadCount(2L)).thenReturn(5L);

        mockMvc.perform(get("/notifications/me/unread-count").header("Authorization", pendingUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    void markRead_own_returnsNotification() throws Exception {
        when(notificationService.markRead(2L, 1L)).thenReturn(ITEM);

        mockMvc.perform(patch("/notifications/1/read").header("Authorization", pendingUser()))
                .andExpect(status().isOk());
    }

    @Test
    void markRead_notMine_returns404() throws Exception {
        when(notificationService.markRead(2L, 1L)).thenThrow(new NotFoundException("Notification 1 not found"));

        mockMvc.perform(patch("/notifications/1/read").header("Authorization", pendingUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void readAll_returnsUpdated() throws Exception {
        when(notificationService.markAllRead(2L)).thenReturn(3);

        mockMvc.perform(post("/notifications/me/read-all").header("Authorization", pendingUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(3));
    }

    @Test
    void internalCreate_withToken_returns201() throws Exception {
        when(notificationService.create(any())).thenReturn(2);

        mockMvc.perform(post("/internal/notifications").header(InternalTokenFilter.HEADER, "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(2));
    }

    @Test
    void internalCreate_userJwt_returns403() throws Exception {
        mockMvc.perform(post("/internal/notifications").header("Authorization", pendingUser())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verify(notificationService, never()).create(any());
    }

    @Test
    void internalCreate_noToken_returns401() throws Exception {
        mockMvc.perform(post("/internal/notifications").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalCreate_unknownKindAndNoRecipients_returns400() throws Exception {
        mockMvc.perform(post("/internal/notifications").header(InternalTokenFilter.HEADER, "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientIds\":[],\"kind\":\"task_assigned\",\"message\":\"m\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/internal/notifications").header(InternalTokenFilter.HEADER, "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientIds\":[1],\"kind\":\"party\",\"message\":\"m\"}"))
                .andExpect(status().isBadRequest());
    }
}
