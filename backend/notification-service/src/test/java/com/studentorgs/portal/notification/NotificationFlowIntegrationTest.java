package com.studentorgs.portal.notification;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.client.NotificationRequest;
import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.security.AuthenticatedUser;
import com.studentorgs.portal.common.security.InternalTokenFilter;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;

/** Internal create → unread count → mark read → read all, on in-memory H2. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private UserDirectoryClient users;

    private String as(long id) {
        return "Bearer " + jwtService.issue(new AuthenticatedUser(id, "u@uta.edu", "U", Role.MEMBER, UserStatus.ACTIVE));
    }

    /** Sends exactly what common's NotificationClient sends, so the two stay compatible. */
    private void send(NotificationRequest request) throws Exception {
        mockMvc.perform(post("/internal/notifications").header(InternalTokenFilter.HEADER, "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void createReadAndReadAll_onlyOwnNotifications() throws Exception {
        when(users.status(20L)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.PENDING, Role.MEMBER)));
        when(users.status(21L)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.MEMBER)));

        send(new NotificationRequest(List.of(20L, 21L), NotificationKind.ANNOUNCEMENT, "Meeting moved", "announcement", 7L));
        send(new NotificationRequest(List.of(20L), NotificationKind.APPLICATION_DECISION, "Rejected", "membership_application", 3L));

        // A pending user can read their own notifications (e.g. the decision).
        mockMvc.perform(get("/notifications/me/unread-count").header("Authorization", as(20)))
                .andExpect(jsonPath("$.count").value(2));
        String page = mockMvc.perform(get("/notifications/me").header("Authorization", as(20)))
                .andExpect(jsonPath("$.content[0].kind").value("application_decision"))
                .andReturn().getResponse().getContentAsString();
        long newest = objectMapper.readTree(page).get("content").get(0).get("id").asLong();

        // User 21 cannot read user 20's notification.
        mockMvc.perform(patch("/notifications/{id}/read", newest).header("Authorization", as(21)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/notifications/{id}/read", newest).header("Authorization", as(20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
        mockMvc.perform(get("/notifications/me").param("unreadOnly", "true").header("Authorization", as(20)))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(post("/notifications/me/read-all").header("Authorization", as(20)))
                .andExpect(jsonPath("$.updated").value(1));
        mockMvc.perform(get("/notifications/me/unread-count").header("Authorization", as(20)))
                .andExpect(jsonPath("$.count").value(0));
        mockMvc.perform(get("/notifications/me/unread-count").header("Authorization", as(21)))
                .andExpect(jsonPath("$.count").value(1));
    }
}
