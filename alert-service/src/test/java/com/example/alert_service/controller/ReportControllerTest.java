package com.example.alert_service.controller;

import com.example.alert_service.dto.BrokenLinkDto;
import com.example.alert_service.dto.SiteReportResponse;
import com.example.alert_service.service.ReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReportControllerTest {

    @Mock private ReportService reportService;
    @InjectMocks private ReportController reportController;

    @Test
    void getReport_returns200WithBody() {
        UUID siteId = UUID.randomUUID();
        SiteReportResponse expected = new SiteReportResponse(siteId, 0, List.of());
        when(reportService.getReport(siteId)).thenReturn(expected);

        ResponseEntity<SiteReportResponse> response = reportController.getReport(siteId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    void getReport_delegatesToReportService() {
        UUID siteId = UUID.randomUUID();
        when(reportService.getReport(siteId)).thenReturn(new SiteReportResponse(siteId, 0, List.of()));

        reportController.getReport(siteId);

        verify(reportService).getReport(siteId);
    }

    @Test
    void getReport_returnsAlertListInBody() {
        UUID siteId = UUID.randomUUID();
        List<BrokenLinkDto> alerts = List.of(
                new BrokenLinkDto(UUID.randomUUID(), "LINK_BROKEN", LocalDateTime.now(), true)
        );
        SiteReportResponse expected = new SiteReportResponse(siteId, 1, alerts);
        when(reportService.getReport(siteId)).thenReturn(expected);

        ResponseEntity<SiteReportResponse> response = reportController.getReport(siteId);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalAlerts()).isEqualTo(1);
        assertThat(response.getBody().getAlerts()).hasSize(1);
    }
}
