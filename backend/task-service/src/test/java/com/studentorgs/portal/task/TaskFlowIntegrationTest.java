package com.studentorgs.portal.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.studentorgs.portal.common.client.NotificationClient;
import com.studentorgs.portal.common.client.NotificationKind;
import com.studentorgs.portal.common.client.UserDirectoryClient;
import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.task.repository.TaskRepository;

/** Committees and tasks end to end on in-memory H2; user-service and notification-service are mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskFlowIntegrationTest {

    private static final long OFFICER_ADMIN_ID = 1L;
    private static final long ANA = 2L;
    private static final long BOB = 3L;
    private static final long PENDING = 4L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private TaskRepository taskRepository;
    @MockBean
    private UserDirectoryClient users;
    @MockBean
    private NotificationClient notificationClient;

    @BeforeEach
    void setUp() {
        when(users.status(OFFICER_ADMIN_ID)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.OFFICER_ADMIN)));
        when(users.status(ANA)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.MEMBER)));
        when(users.status(BOB)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.ACTIVE, Role.MEMBER)));
        when(users.status(PENDING)).thenReturn(Optional.of(new UserStatusInfo(UserStatus.PENDING, Role.MEMBER)));
        when(users.names(anyCollection())).thenReturn(Map.of(ANA, "Ana", BOB, "Bob"));
    }

    private String as(long id, Role role) {
        return TestTasks.bearer(jwtService, id, role);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long committeeWithMembers(Long... userIds) throws Exception {
        String name = "Committee " + UUID.randomUUID();
        long id = json(mockMvc.perform(post("/committees").header("Authorization", as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\",\"kind\":\"PROJECT\"}".formatted(name)))
                .andExpect(status().isCreated())).get("id").asLong();
        mockMvc.perform(post("/committees/{id}/members", id).header("Authorization", as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userIds", userIds))))
                .andExpect(status().isOk());
        return id;
    }

    private long createTask(long committeeId, long assigneeId) throws Exception {
        return json(mockMvc.perform(post("/tasks").header("Authorization", as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"committeeId\":%d,\"title\":\"Book room\",\"assigneeId\":%d,\"dueDate\":\"2026-12-01\"}"
                                .formatted(committeeId, assigneeId)))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions move(long userId, Role role, long taskId, String next) throws Exception {
        return mockMvc.perform(patch("/tasks/{id}/status", taskId).header("Authorization", as(userId, role))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + next + "\"}"));
    }

    @Test
    void assignDoneReopen_completedAtSetThenCleared() throws Exception {
        long committeeId = committeeWithMembers(ANA, BOB);
        mockMvc.perform(get("/committees/{id}", committeeId).header("Authorization", as(ANA, Role.MEMBER)))
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].displayName").value("Ana"));

        long taskId = createTask(committeeId, ANA);
        verify(notificationClient).send(argThat(n -> n.kind() == NotificationKind.TASK_ASSIGNED
                && n.recipientIds().contains(ANA) && n.referenceId() == taskId));

        move(ANA, Role.MEMBER, taskId, "DONE").andExpect(status().isUnprocessableEntity());
        move(BOB, Role.MEMBER, taskId, "IN_PROGRESS").andExpect(status().isForbidden());
        move(ANA, Role.MEMBER, taskId, "IN_PROGRESS").andExpect(status().isOk());
        move(ANA, Role.MEMBER, taskId, "DONE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        assertThat(taskRepository.findById(taskId).orElseThrow().getCompletedAt()).isNotNull();

        // An officer reopens it: completed_at is cleared in the database too.
        move(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN, taskId, "IN_PROGRESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
        assertThat(taskRepository.findById(taskId).orElseThrow().getCompletedAt()).isNull();
    }

    @Test
    void assignee_mustBeCommitteeMember_onCreateAndReassign() throws Exception {
        long committeeId = committeeWithMembers(ANA);

        mockMvc.perform(post("/tasks").header("Authorization", as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"committeeId\":%d,\"title\":\"t\",\"assigneeId\":%d}".formatted(committeeId, BOB)))
                .andExpect(status().isUnprocessableEntity());

        long taskId = createTask(committeeId, ANA);
        mockMvc.perform(put("/tasks/{id}", taskId).header("Authorization", as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"t\",\"assigneeId\":%d}".formatted(BOB)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void removeMember_withOpenTask_blockedUntilDone() throws Exception {
        long committeeId = committeeWithMembers(ANA, BOB);
        long taskId = createTask(committeeId, ANA);
        String officer = as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN);

        mockMvc.perform(delete("/committees/{id}/members/{u}", committeeId, ANA).header("Authorization", officer))
                .andExpect(status().isUnprocessableEntity());

        move(ANA, Role.MEMBER, taskId, "IN_PROGRESS");
        move(ANA, Role.MEMBER, taskId, "DONE");
        mockMvc.perform(delete("/committees/{id}/members/{u}", committeeId, ANA).header("Authorization", officer))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/committees/{id}/members/{u}", committeeId, ANA).header("Authorization", officer))
                .andExpect(status().isNotFound());
    }

    @Test
    void committees_duplicateNameAndInactiveMembers_rejected() throws Exception {
        String officer = as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN);
        mockMvc.perform(post("/committees").header("Authorization", officer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Outreach\",\"kind\":\"COMMITTEE\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/committees").header("Authorization", officer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"OUTREACH\",\"kind\":\"PROJECT\"}"))
                .andExpect(status().isConflict());

        long committeeId = committeeWithMembers(ANA);
        mockMvc.perform(post("/committees/{id}/members", committeeId).header("Authorization", officer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"userIds\":[%d]}".formatted(PENDING)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void batch_allOrNothing_thenInternalMembers() throws Exception {
        long committeeId = committeeWithMembers(ANA, BOB);
        String officer = as(OFFICER_ADMIN_ID, Role.OFFICER_ADMIN);

        mockMvc.perform(post("/tasks/batch").header("Authorization", officer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"committeeId\":%d,\"items\":[{\"title\":\"Room\",\"assigneeId\":%d},{\"title\":\"Food\",\"assigneeId\":%d}]}"
                                .formatted(committeeId, ANA, PENDING)))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/tasks").param("committeeId", String.valueOf(committeeId)).header("Authorization", officer))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(post("/tasks/batch").header("Authorization", officer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"committeeId\":%d,\"items\":[{\"title\":\"Room\",\"assigneeId\":%d},{\"title\":\"Food\",\"assigneeId\":%d,\"dueDate\":\"2026-12-01\"}]}"
                                .formatted(committeeId, ANA, BOB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/internal/committees/{id}/members", committeeId).header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].displayName").value("Ana"));
    }

    @Test
    void listsAndMyTasks_filterAndAccess() throws Exception {
        long committeeId = committeeWithMembers(ANA, BOB);
        long anaTask = createTask(committeeId, ANA);
        createTask(committeeId, BOB);

        mockMvc.perform(get("/tasks/me").header("Authorization", as(ANA, Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(anaTask)).exists());
        mockMvc.perform(get("/tasks").param("committeeId", String.valueOf(committeeId)).param("assigneeId", String.valueOf(BOB))
                        .header("Authorization", as(ANA, Role.MEMBER)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/tasks/me").header("Authorization", as(PENDING, Role.MEMBER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/committees").header("Authorization", as(ANA, Role.MEMBER)))
                .andExpect(status().isOk());
    }
}
