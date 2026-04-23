package com.example.alert_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class SiteReportResponse {
    private UUID siteId;
    private int totalAlerts;
    private List<BrokenLinkDto> alerts;
}