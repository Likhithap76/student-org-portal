package com.studentorgs.portal.content;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.studentorgs.portal.common.client.NotificationClient;
import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.client.NotificationRequest;
import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;

/** Announcements and documents end to end on in-memory H2; user-service and notification-service are mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentFlowIntegrationTest {

    private static final long OFFICER_ADMIN_ID = 1L;
    private static final long OTHER_OFFICER_ADMIN_ID = 5L;
    private static final long ANA = 2L;
    private static final long THIRD_OFFICER_ADMIN_ID = 9L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private UserDirectoryClient users;
    @MockBean
    private NotificationClient notificationClient;

    @BeforeEach
    void setUp() {
        when(users.status(OFFICER_ADMIN_ID)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.OFFICER_ADMIN)));
        when(users.status(OTHER_OFFICER_ADMIN_ID)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.OFFICER_ADMIN)));
        when(users.status(ANA)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.MEMBER)));
        when(users.status(THIRD_OFFICER_ADMIN_ID)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.OFFICER_ADMIN)));
    }

    private String as(long id, Role role) {
        return TestContent.bearer(jwtService, id, role);
    }

    @Test
    void publishAnnouncement_notifiesAllActiveUsers() throws Exception {
        when(users.activeIds()).thenReturn(List.of(OFFICER_ADMIN_ID, ANA, THIRD_OFFICER_ADMIN_ID));

        String json = mockMvc.perform(post("/announcements").header("Authorization", as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Meeting moved\",\"body\":\"Room 200\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(json).get("id").asLong();

        verify(notificationClient).send(new NotificationRequest(List.of(OFFICER_ADMIN_ID, ANA, THIRD_OFFICER_ADMIN_ID),
                NotificationKind.ANNOUNCEMENT, "New announcement: Meeting moved", "announcement", id));
        mockMvc.perform(get("/announcements").header("Authorization", as(ANA, Role.MEMBER)))
                .andExpect(jsonPath("$.content[0].title").value("Meeting moved"));

        // A member cannot delete it; any officer_admin can.
        mockMvc.perform(delete("/announcements/{id}", id).header("Authorization", as(ANA, Role.MEMBER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/announcements/{id}", id).header("Authorization", as(OTHER_OFFICER_ADMIN_ID, Role.OFFICER_ADMIN)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/announcements/{id}", id).header("Authorization", as(THIRD_OFFICER_ADMIN_ID, Role.OFFICER_ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void publishAnnouncement_invalid_noNotification() throws Exception {
        mockMvc.perform(post("/announcements").header("Authorization", as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\",\"body\":\"x\"}"))
                .andExpect(status().isBadRequest());
        verify(notificationClient, never()).send(any());
    }

    @Test
    void documents_createSearchUpdateDelete() throws Exception {
        String officer = as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN);
        String json = mockMvc.perform(post("/documents").header("Authorization", officer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Dues Form 2026\",\"category\":\"Forms\",\"url\":\"https://drive.example.edu/dues\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(json).get("id").asLong();
        mockMvc.perform(post("/documents").header("Authorization", officer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Bylaws\",\"category\":\"Governance\",\"url\":\"https://drive.example.edu/bylaws\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/documents").header("Authorization", officer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Old\",\"category\":\"Forms\",\"url\":\"http://insecure.example.edu\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/documents").param("category", "forms").param("q", "DUES").header("Authorization", as(ANA, Role.MEMBER)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Dues Form 2026"));

        mockMvc.perform(put("/documents/{id}", id).header("Authorization", as(ANA, Role.MEMBER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"category\":\"Forms\",\"url\":\"https://x.edu\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/documents/{id}", id).header("Authorization", officer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Dues Form 2027\",\"category\":\"Forms\",\"url\":\"https://drive.example.edu/dues27\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dues Form 2027"));
        mockMvc.perform(delete("/documents/{id}", id).header("Authorization", officer))
                .andExpect(status().isNoContent());
    }
}
