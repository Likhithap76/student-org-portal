package com.studentorgs.portal.ai.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.ai.dto.SummaryRequest;
import com.studentorgs.portal.ai.dto.SummaryResponse;
import com.studentorgs.portal.ai.service.AiSummaryService;
import com.studentorgs.portal.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiSummaryController {

    private final AiSummaryService summaryService;

    @PostMapping("/summaries")
    @PreAuthorize("hasRole('OFFICER_ADMIN')")
    @Operation(summary = "Summarize meeting notes into a summary, decisions and suggested tasks (OFFICER_ADMIN). Nothing is saved")
    public SummaryResponse summarize(@Valid @RequestBody SummaryRequest request) {
        return summaryService.summarize(CurrentUser.id(), request);
    }
}
