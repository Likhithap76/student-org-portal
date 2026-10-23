package com.studentorgs.portal.reporting.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.common.security.CurrentUser;
import com.studentorgs.portal.reporting.dto.EngagementReport;
import com.studentorgs.portal.reporting.dto.SummaryResponse;
import com.studentorgs.portal.reporting.service.CsvWriter;
import com.studentorgs.portal.reporting.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OFFICER_ADMIN')")
public class ReportController {

    static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ReportService reportService;

    @GetMapping("/engagement")
    @Operation(summary = "FR-07: events attended and tasks completed per member (OFFICER_ADMIN). Dates inclusive; default last 30 days")
    public EngagementReport engagement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return reportService.engagement(CurrentUser.id(), from, to, includeInactive);
    }

    @GetMapping("/engagement.csv")
    @Operation(summary = "FR-07 engagement report as a CSV download (OFFICER_ADMIN)")
    public ResponseEntity<String> engagementCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        EngagementReport report = reportService.engagement(CurrentUser.id(), from, to, includeInactive);
        String filename = "engagement-" + report.from() + "-" + report.to() + ".csv";
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(CsvWriter.engagement(report.rows()));
    }

    @GetMapping("/summary")
    @Operation(summary = "Totals: active members, pending applications, upcoming events, open and overdue tasks (OFFICER_ADMIN)")
    public SummaryResponse summary() {
        return reportService.summary(CurrentUser.id());
    }
}
