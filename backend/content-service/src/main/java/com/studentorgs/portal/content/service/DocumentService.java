package com.studentorgs.portal.content.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studentorgs.portal.common.client.UserDirectoryClient.UserStatusInfo;
import com.studentorgs.portal.common.error.BusinessRuleException;
import com.studentorgs.portal.common.error.ForbiddenOperationException;
import com.studentorgs.portal.common.error.NotFoundException;
import com.studentorgs.portal.common.paging.PageRequests;
import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.MemberGuard;
import com.studentorgs.portal.common.security.Role;
import com.studentorgs.portal.content.domain.DocumentLink;
import com.studentorgs.portal.content.dto.DocumentRequest;
import com.studentorgs.portal.content.dto.DocumentResponse;
import com.studentorgs.portal.content.repository.DocumentLinkRepository;
import lombok.RequiredArgsConstructor;

/** Document link repository: links only, https URLs only. */
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Sort BY_TITLE = Sort.by("title").ascending().and(Sort.by("id").ascending());

    private final DocumentLinkRepository documents;
    private final MemberGuard guard;
    private final Clock clock;

    @Transactional
    public DocumentResponse create(Long officerId, DocumentRequest request) {
        guard.requireRole(officerId, Role.OFFICER_ADMIN);
        String url = requireHttpsUrl(request.url());
        return DocumentResponse.from(documents.save(new DocumentLink(request.title().trim(), request.category().trim(),
                url, officerId, clock.instant())));
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(Long userId, String category, String q, Integer page, Integer size) {
        guard.requireActive(userId);
        Specification<DocumentLink> filter = Specification.where(DocumentLinkRepository.inCategory(category))
                .and(DocumentLinkRepository.titleContains(q));
        return PageResponse.from(documents.findAll(filter, PageRequests.of(page, size, BY_TITLE)), DocumentResponse::from);
    }

    @Transactional
    public DocumentResponse update(Long userId, Long documentId, DocumentRequest request) {
        DocumentLink document = findOwnedOrOfficerAdmin(userId, documentId);
        document.update(request.title().trim(), request.category().trim(), requireHttpsUrl(request.url()), clock.instant());
        return DocumentResponse.from(document);
    }

    @Transactional
    public void delete(Long userId, Long documentId) {
        documents.delete(findOwnedOrOfficerAdmin(userId, documentId));
    }

    /** The creator or any officer_admin may change a link. */
    private DocumentLink findOwnedOrOfficerAdmin(Long userId, Long documentId) {
        UserStatusInfo caller = guard.requireActive(userId);
        DocumentLink document = documents.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document " + documentId + " not found"));
        if (!document.getCreatedBy().equals(userId) && caller.role() != Role.OFFICER_ADMIN) {
            throw new ForbiddenOperationException("Only the creator or an officer_admin can change this document");
        }
        return document;
    }

    /** Must start with https:// and be a well-formed URL with a host. */
    static String requireHttpsUrl(String raw) {
        String url = raw.trim();
        if (!url.startsWith("https://")) {
            throw new BusinessRuleException("The URL must start with https://");
        }
        try {
            URI uri = new URI(url);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BusinessRuleException("The URL must include a host name");
            }
        } catch (URISyntaxException e) {
            throw new BusinessRuleException("The URL is not valid");
        }
        return url;
    }
}
