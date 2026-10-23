package com.studentorgs.portal.content.service;

import static com.studentorgs.portal.content.TestContent.NOW;
import static com.studentorgs.portal.content.TestContent.document;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.common.security.UserStatus;
import com.studentorgs.portal.content.domain.DocumentLink;
import com.studentorgs.portal.content.dto.DocumentRequest;
import com.studentorgs.portal.content.dto.DocumentResponse;
import com.studentorgs.portal.content.repository.DocumentLinkRepository;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentLinkRepository documents;
    @Mock
    private MemberGuard guard;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documents, guard, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static UserStatusInfo active(Role role) {
        return new UserStatusInfo(UserStatus.ACTIVE, role);
    }

    @Test
    void create_httpsUrl_saves() {
        when(documents.save(any(DocumentLink.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentResponse response = service.create(1L,
                new DocumentRequest(" Bylaws ", " Governance ", " https://drive.example.edu/bylaws "));

        assertThat(response.url()).isEqualTo("https://drive.example.edu/bylaws");
        assertThat(response.category()).isEqualTo("Governance");
        assertThat(response.createdBy()).isEqualTo(1L);
        verify(guard).requireRole(1L, Role.OFFICER_ADMIN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.edu", "ftp://example.edu", "example.edu", "javascript:alert(1)",
            "HTTPS://example.edu", "https://", "https:///path", "https://exa mple.edu"})
    void requireHttpsUrl_invalid_throwsBusinessRule(String url) {
        assertThatThrownBy(() -> DocumentService.requireHttpsUrl(url)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void create_httpUrl_throwsAndSavesNothing() {
        assertThatThrownBy(() -> service.create(1L, new DocumentRequest("t", "c", "http://example.edu")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("https://");
        verify(documents, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_member_returnsPage() {
        when(documents.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document(4, 1)), PageRequest.of(0, 20), 1));

        assertThat(service.list(2L, "Forms", "doc", null, null).content()).hasSize(1);
        verify(guard).requireActive(2L);
    }

    @Test
    void update_creator_updatesAndChecksUrl() {
        DocumentLink d = document(4, 1);
        when(guard.requireActive(1L)).thenReturn(active(Role.OFFICER_ADMIN));
        when(documents.findById(4L)).thenReturn(Optional.of(d));

        service.update(1L, 4L, new DocumentRequest("New", "Forms", "https://example.edu/new"));

        assertThat(d.getTitle()).isEqualTo("New");
        assertThat(d.getUrl()).isEqualTo("https://example.edu/new");
        assertThat(d.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void update_creatorWithHttpUrl_throwsBusinessRule() {
        when(guard.requireActive(1L)).thenReturn(active(Role.OFFICER_ADMIN));
        when(documents.findById(4L)).thenReturn(Optional.of(document(4, 1)));

        assertThatThrownBy(() -> service.update(1L, 4L, new DocumentRequest("t", "c", "http://x.edu")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void update_memberNotCreator_throwsForbidden() {
        when(guard.requireActive(3L)).thenReturn(active(Role.MEMBER));
        when(documents.findById(4L)).thenReturn(Optional.of(document(4, 1)));

        assertThatThrownBy(() -> service.update(3L, 4L, new DocumentRequest("t", "c", "https://x.edu")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void delete_otherOfficerAdmin_deletesSomeoneElses() {
        DocumentLink d = document(4, 1);
        when(guard.requireActive(9L)).thenReturn(active(Role.OFFICER_ADMIN));
        when(documents.findById(4L)).thenReturn(Optional.of(d));

        service.delete(9L, 4L);

        verify(documents).delete(d);
    }

    @Test
    void delete_unknown_throwsNotFound() {
        when(guard.requireActive(1L)).thenReturn(active(Role.OFFICER_ADMIN));
        when(documents.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L, 4L)).isInstanceOf(NotFoundException.class);
    }
}
