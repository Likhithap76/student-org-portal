package com.studentorgs.portal.content.controller;

import static com.studentorgs.portal.content.TestContent.NOW;
import static com.studentorgs.portal.content.TestContent.bearer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.studentorgs.portal.content.dto.AnnouncementResponse;
import com.studentorgs.portal.content.dto.DocumentResponse;
import com.studentorgs.portal.content.service.AnnouncementService;
import com.studentorgs.portal.content.service.DocumentService;

@WebMvcTest({AnnouncementController.class, DocumentController.class})
@ActiveProfiles("test")
class ContentControllersTest {

    private static final AnnouncementResponse ANN = new AnnouncementResponse(7L, "Meeting", "Body", 1L, NOW);
    private static final DocumentResponse DOC = new DocumentResponse(4L, "Bylaws", "Governance",
            "https://example.edu/bylaws", 1L, NOW, NOW);
    private static final String DOC_BODY = "{\"title\":\"Bylaws\",\"category\":\"Governance\",\"url\":\"https://example.edu/bylaws\"}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockBean
    private AnnouncementService announcementService;
    @MockBean
    private DocumentService documentService;

    private String officer() {
        return bearer(jwtService, 1, Role.OFFICER_ADMIN);
    }

    private String member() {
        return bearer(jwtService, 2, Role.MEMBER);
    }

    // --- announcements ---

    @Test
    void publish_officer_returns201() throws Exception {
        when(announcementService.publish(eq(1L), any())).thenReturn(ANN);

        mockMvc.perform(post("/announcements").header("Authorization", officer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Meeting\",\"body\":\"Body\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void publish_blankBody_returns400() throws Exception {
        mockMvc.perform(post("/announcements").header("Authorization", officer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Meeting\",\"body\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("body"));
    }

    @Test
    void publish_member_returns403() throws Exception {
        mockMvc.perform(post("/announcements").header("Authorization", member())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Meeting\",\"body\":\"Body\"}"))
                .andExpect(status().isForbidden());
        verify(announcementService, never()).publish(anyLong(), any());
    }

    @Test
    void listAnnouncements_noToken_returns401() throws Exception {
        mockMvc.perform(get("/announcements")).andExpect(status().isUnauthorized());
    }

    @Test
    void listAnnouncements_member_returnsPage() throws Exception {
        when(announcementService.list(2L, null, null)).thenReturn(new PageResponse<>(List.of(ANN), 0, 20, 1, 1, true));

        mockMvc.perform(get("/announcements").header("Authorization", member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Meeting"));
    }

    @Test
    void deleteAnnouncement_publisher_returns204() throws Exception {
        mockMvc.perform(delete("/announcements/7").header("Authorization", officer()))
                .andExpect(status().isNoContent());
        verify(announcementService).delete(1L, 7L);
    }

    @Test
    void deleteAnnouncement_notPublisher_returns403() throws Exception {
        doThrow(new ForbiddenOperationException("Only the publisher or an admin")).when(announcementService).delete(2L, 7L);

        mockMvc.perform(delete("/announcements/7").header("Authorization", member()))
                .andExpect(status().isForbidden());
    }

    // --- documents ---

    @Test
    void createDocument_officer_returns201() throws Exception {
        when(documentService.create(eq(1L), any())).thenReturn(DOC);

        mockMvc.perform(post("/documents").header("Authorization", officer())
                        .contentType(MediaType.APPLICATION_JSON).content(DOC_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://example.edu/bylaws"));
    }

    @Test
    void createDocument_httpUrl_returns422() throws Exception {
        when(documentService.create(eq(1L), any())).thenThrow(new BusinessRuleException("The URL must start with https://"));

        mockMvc.perform(post("/documents").header("Authorization", officer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"category\":\"c\",\"url\":\"http://example.edu\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("The URL must start with https://"));
    }

    @Test
    void createDocument_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/documents").header("Authorization", officer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void createDocument_member_returns403() throws Exception {
        mockMvc.perform(post("/documents").header("Authorization", member())
                        .contentType(MediaType.APPLICATION_JSON).content(DOC_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void listDocuments_filters_passedToService() throws Exception {
        when(documentService.list(2L, "Forms", "dues", 0, 10)).thenReturn(new PageResponse<>(List.of(DOC), 0, 10, 1, 1, true));

        mockMvc.perform(get("/documents").param("category", "Forms").param("q", "dues").param("page", "0")
                        .param("size", "10").header("Authorization", member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(4));
    }

    @Test
    void listDocuments_noToken_returns401() throws Exception {
        mockMvc.perform(get("/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void updateDocument_creator_returnsDocument() throws Exception {
        when(documentService.update(eq(1L), eq(4L), any())).thenReturn(DOC);

        mockMvc.perform(put("/documents/4").header("Authorization", officer())
                        .contentType(MediaType.APPLICATION_JSON).content(DOC_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void updateDocument_notCreator_returns403() throws Exception {
        when(documentService.update(eq(2L), eq(4L), any())).thenThrow(new ForbiddenOperationException("no"));

        mockMvc.perform(put("/documents/4").header("Authorization", member())
                        .contentType(MediaType.APPLICATION_JSON).content(DOC_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteDocument_creator_returns204() throws Exception {
        mockMvc.perform(delete("/documents/4").header("Authorization", officer()))
                .andExpect(status().isNoContent());
        verify(documentService).delete(1L, 4L);
    }
}
