package com.example.alert_service.service;

import com.example.alert_service.dto.BrokenLinkDto;
import com.example.alert_service.dto.SiteReportResponse;
import com.example.alert_service.repository.AlertLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final AlertLogRepository alertLogRepository;

    public SiteReportResponse getReport(UUID siteId) {
        var alerts = alertLogRepository
                .findBySiteIdOrderByFiredAtDesc(siteId);

        List<BrokenLinkDto> alertDtos = alerts.stream()
                .map(a -> new BrokenLinkDto(
                        a.getLinkId(),
                        a.getAlertType(),
                        a.getFiredAt(),
                        a.getDelivered()
                ))
                .collect(Collectors.toList());

        return new SiteReportResponse(siteId, alerts.size(), alertDtos);
    }
}