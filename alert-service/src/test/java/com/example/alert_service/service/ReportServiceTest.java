package com.example.alert_service.service;

import com.example.alert_service.dto.SiteReportResponse;
import com.example.alert_service.model.AlertLog;
import com.example.alert_service.repository.AlertLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ReportServiceTest {

    @Mock private AlertLogRepository alertLogRepository;
    @InjectMocks private ReportService reportService;

    @Test
    void getReport_noAlerts_returnsZeroTotal() {
        UUID siteId = UUID.randomUUID();
        when(alertLogRepository.findBySiteIdOrderByFiredAtDesc(siteId)).thenReturn(List.of());

        SiteReportResponse response = reportService.getReport(siteId);

        assertThat(response.getSiteId()).isEqualTo(siteId);
        assertThat(response.getTotalAlerts()).isEqualTo(0);
        assertThat(response.getAlerts()).isEmpty();
    }

    @Test
    void getReport_multipleAlerts_returnsMappedDtos() {
        UUID siteId = UUID.randomUUID();
        UUID linkId1 = UUID.randomUUID();
        UUID linkId2 = UUID.randomUUID();
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(30);
        LocalDateTime t2 = LocalDateTime.now().minusMinutes(10);

        AlertLog log1 = AlertLog.builder()
                .id(UUID.randomUUID()).siteId(siteId).linkId(linkId1)
                .alertType("LINK_BROKEN").firedAt(t1).delivered(true).build();
        AlertLog log2 = AlertLog.builder()
                .id(UUID.randomUUID()).siteId(siteId).linkId(linkId2)
                .alertType("LINK_RECOVERED").firedAt(t2).delivered(false).build();

        when(alertLogRepository.findBySiteIdOrderByFiredAtDesc(siteId))
                .thenReturn(List.of(log1, log2));

        SiteReportResponse response = reportService.getReport(siteId);

        assertThat(response.getTotalAlerts()).isEqualTo(2);
        assertThat(response.getAlerts()).hasSize(2);

        assertThat(response.getAlerts().get(0).getLinkId()).isEqualTo(linkId1);
        assertThat(response.getAlerts().get(0).getAlertType()).isEqualTo("LINK_BROKEN");
        assertThat(response.getAlerts().get(0).getFiredAt()).isEqualTo(t1);
        assertThat(response.getAlerts().get(0).getDelivered()).isTrue();

        assertThat(response.getAlerts().get(1).getLinkId()).isEqualTo(linkId2);
        assertThat(response.getAlerts().get(1).getAlertType()).isEqualTo("LINK_RECOVERED");
        assertThat(response.getAlerts().get(1).getDelivered()).isFalse();
    }

    @Test
    void getReport_queriesRepositoryBySiteId() {
        UUID siteId = UUID.randomUUID();
        when(alertLogRepository.findBySiteIdOrderByFiredAtDesc(siteId)).thenReturn(List.of());

        reportService.getReport(siteId);

        verify(alertLogRepository).findBySiteIdOrderByFiredAtDesc(siteId);
    }

    @Test
    void getReport_totalMatchesListSize() {
        UUID siteId = UUID.randomUUID();
        List<AlertLog> logs = List.of(
                AlertLog.builder().id(UUID.randomUUID()).siteId(siteId).linkId(UUID.randomUUID())
                        .alertType("LINK_BROKEN").firedAt(LocalDateTime.now()).delivered(true).build(),
                AlertLog.builder().id(UUID.randomUUID()).siteId(siteId).linkId(UUID.randomUUID())
                        .alertType("LINK_BROKEN").firedAt(LocalDateTime.now()).delivered(false).build(),
                AlertLog.builder().id(UUID.randomUUID()).siteId(siteId).linkId(UUID.randomUUID())
                        .alertType("LINK_RECOVERED").firedAt(LocalDateTime.now()).delivered(true).build()
        );
        when(alertLogRepository.findBySiteIdOrderByFiredAtDesc(siteId)).thenReturn(logs);

        SiteReportResponse response = reportService.getReport(siteId);

        assertThat(response.getTotalAlerts()).isEqualTo(3);
        assertThat(response.getAlerts()).hasSize(3);
    }
}
