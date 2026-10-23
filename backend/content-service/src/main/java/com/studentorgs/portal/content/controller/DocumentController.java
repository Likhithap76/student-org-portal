package com.studentorgs.portal.content.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.common.paging.PageResponse;
import com.studentorgs.portal.common.security.CurrentUser;
import com.studentorgs.portal.content.dto.DocumentRequest;
import com.studentorgs.portal.content.dto.DocumentResponse;
import com.studentorgs.portal.content.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Save a document link (OFFICER_ADMIN). URL must start with https://")
    public DocumentResponse create(@Valid @RequestBody DocumentRequest request) {
        return documentService.create(CurrentUser.id(), request);
    }

    @GetMapping
    @Operation(summary = "Document links by category and title search (active members)")
    public PageResponse<DocumentResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return documentService.list(CurrentUser.id(), category, q, page, size);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a document link (creator or OFFICER_ADMIN)")
    public DocumentResponse update(@PathVariable Long id, @Valid @RequestBody DocumentRequest request) {
        return documentService.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document link (creator or OFFICER_ADMIN)")
    public void delete(@PathVariable Long id) {
        documentService.delete(CurrentUser.id(), id);
    }
}
