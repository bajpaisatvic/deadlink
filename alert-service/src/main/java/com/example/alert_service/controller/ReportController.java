package com.example.alert_service.controller;

import com.example.alert_service.dto.SiteReportResponse;
import com.example.alert_service.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/{siteId}")
    public ResponseEntity<SiteReportResponse> getReport(@PathVariable UUID siteId) {
        return ResponseEntity.ok(reportService.getReport(siteId));
    }
}