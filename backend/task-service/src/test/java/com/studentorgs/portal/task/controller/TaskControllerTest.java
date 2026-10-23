package com.studentorgs.portal.task.controller;

import static com.studentorgs.portal.task.TestTasks.NOW;
import static com.studentorgs.portal.task.TestTasks.TODAY;
import static com.studentorgs.portal.task.TestTasks.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.JwtService;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.task.domain.TaskStatus;
import com.studentorgs.portal.task.dto.TaskResponse;
import com.studentorgs.portal.task.dto.TaskStatusRequest;
import com.studentorgs.portal.task.service.TaskService;

@WebMvcTest(TaskController.class)
@ActiveProfiles("test")
class TaskControllerTest {

    private static final TaskResponse TASK = new TaskResponse(40L, 5L, "Book room", 2L, TODAY, TaskStatus.TODO, null,
            1L, NOW, NOW, false);
    private static final String CREATE_BODY = "{\"committeeId\":5,\"title\":\"Book room\",\"assigneeId\":2,\"dueDate\":\"2026-10-25\"}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private TaskService taskService;

    private String officer() {
        return bearer(jwtService, 1, Role.OFFICER_ADMIN);
    }

    private String member() {
        return bearer(jwtService, 2, Role.MEMBER);
    }

    @Test
    void create_officer_returns201() throws Exception {
        when(taskService.create(eq(1L), any())).thenReturn(TASK);

        mockMvc.perform(post("/tasks").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").value("2026-10-20"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void create_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/tasks").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void create_badDate_returns400() throws Exception {
        mockMvc.perform(post("/tasks").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"committeeId\":5,\"title\":\"t\",\"assigneeId\":2,\"dueDate\":\"next week\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_assigneeNotMember_returns422() throws Exception {
        when(taskService.create(eq(1L), any())).thenThrow(new BusinessRuleException("The assignee must be a member of the committee"));

        mockMvc.perform(post("/tasks").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void create_member_returns403() throws Exception {
        mockMvc.perform(post("/tasks").header("Authorization", member()).contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());
        verify(taskService, never()).create(anyLong(), any());
    }

    @Test
    void create_noToken_returns401() throws Exception {
        mockMvc.perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBatch_officer_returns201WithTasks() throws Exception {
        when(taskService.createBatch(eq(1L), any())).thenReturn(List.of(TASK, TASK));

        mockMvc.perform(post("/tasks/batch").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"committeeId\":5,\"items\":[{\"title\":\"a\",\"assigneeId\":2},{\"title\":\"b\",\"assigneeId\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void createBatch_emptyItems_returns400() throws Exception {
        mockMvc.perform(post("/tasks/batch").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"committeeId\":5,\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBatch_itemMissingTitle_returns400() throws Exception {
        mockMvc.perform(post("/tasks/batch").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"committeeId\":5,\"items\":[{\"assigneeId\":2}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBatch_member_returns403() throws Exception {
        mockMvc.perform(post("/tasks/batch").header("Authorization", member()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"committeeId\":5,\"items\":[{\"title\":\"a\",\"assigneeId\":2}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_filters_passedToService() throws Exception {
        when(taskService.list(2L, 5L, 2L, TaskStatus.IN_PROGRESS, 0, 10))
                .thenReturn(new PageResponse<>(List.of(TASK), 0, 10, 1, 1, true));

        mockMvc.perform(get("/tasks").param("committeeId", "5").param("assigneeId", "2").param("status", "IN_PROGRESS")
                        .param("page", "0").param("size", "10").header("Authorization", member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(40));
    }

    @Test
    void list_badStatus_returns400() throws Exception {
        mockMvc.perform(get("/tasks").param("status", "finished").header("Authorization", member()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mine_member_returnsList() throws Exception {
        when(taskService.mine(2L)).thenReturn(List.of(TASK));

        mockMvc.perform(get("/tasks/me").header("Authorization", member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].overdue").value(false));
    }

    @Test
    void mine_noToken_returns401() throws Exception {
        mockMvc.perform(get("/tasks/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void changeStatus_assignee_returnsTask() throws Exception {
        when(taskService.changeStatus(2L, 40L, new TaskStatusRequest(TaskStatus.IN_PROGRESS))).thenReturn(TASK);

        mockMvc.perform(patch("/tasks/40/status").header("Authorization", member())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void changeStatus_illegalMove_returns422() throws Exception {
        when(taskService.changeStatus(2L, 40L, new TaskStatusRequest(TaskStatus.DONE)))
                .thenThrow(new BusinessRuleException("A task cannot move from todo to done"));

        mockMvc.perform(patch("/tasks/40/status").header("Authorization", member())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A task cannot move from todo to done"));
    }

    @Test
    void changeStatus_notAssignee_returns403() throws Exception {
        when(taskService.changeStatus(2L, 40L, new TaskStatusRequest(TaskStatus.IN_PROGRESS)))
                .thenThrow(new ForbiddenOperationException("Only the assignee or an officer"));

        mockMvc.perform(patch("/tasks/40/status").header("Authorization", member())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeStatus_missingStatus_returns400() throws Exception {
        mockMvc.perform(patch("/tasks/40/status").header("Authorization", member())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_officer_returnsTask() throws Exception {
        when(taskService.update(eq(1L), eq(40L), any())).thenReturn(TASK);

        mockMvc.perform(put("/tasks/40").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Book room\",\"assigneeId\":3}"))
                .andExpect(status().isOk());
    }

    @Test
    void update_member_returns403() throws Exception {
        mockMvc.perform(put("/tasks/40").header("Authorization", member()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Book room\",\"assigneeId\":3}"))
                .andExpect(status().isForbidden());
    }
}
