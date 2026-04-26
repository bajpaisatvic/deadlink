package com.example.alert_service.service;

import com.example.alert_service.dto.LinkStatusChanged;
import com.example.alert_service.model.AlertLog;
import com.example.alert_service.repository.AlertLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AlertServiceTest {

    @Mock private AlertLogRepository alertLogRepository;
    @InjectMocks private AlertService alertService;

    private LinkStatusChanged event(String prev, String curr) {
        return new LinkStatusChanged(
                UUID.randomUUID(), UUID.randomUUID(),
                "https://example.com/page",
                prev, curr, 404, LocalDateTime.now()
        );
    }

    private AlertLog savedLog(LinkStatusChanged e, String alertType) {
        return AlertLog.builder()
                .id(UUID.randomUUID())
                .siteId(e.getSiteId())
                .linkId(e.getLinkId())
                .alertType(alertType)
                .delivered(false)
                .build();
    }

    // --- alert type classification ---

    @Test
    void processStatusChange_healthyToBroken_savesLinkBrokenAlert() {
        LinkStatusChanged e = event("HEALTHY", "BROKEN");
        when(alertLogRepository.existsByLinkIdAndAlertTypeAndFiredAtAfter(any(), eq("LINK_BROKEN"), any()))
                .thenReturn(false);
        when(alertLogRepository.save(any())).thenReturn(savedLog(e, "LINK_BROKEN"));

        alertService.processStatusChange(e);

        ArgumentCaptor<AlertLog> captor = ArgumentCaptor.forClass(AlertLog.class);
        verify(alertLogRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getAlertType()).isEqualTo("LINK_BROKEN");
    }

    @Test
    void processStatusChange_healthyToTimeout_savesLinkBrokenAlert() {
        LinkStatusChanged e = event("HEALTHY", "TIMEOUT");
        when(alertLogRepository.existsByLinkIdAndAlertTypeAndFiredAtAfter(any(), eq("LINK_BROKEN"), any()))
                .thenReturn(false);
        when(alertLogRepository.save(any())).thenReturn(savedLog(e, "LINK_BROKEN"));

        alertService.processStatusChange(e);

        ArgumentCaptor<AlertLog> captor = ArgumentCaptor.forClass(AlertLog.class);
        verify(alertLogRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getAlertType()).isEqualTo("LINK_BROKEN");
    }

    @Test
    void processStatusChange_brokenToHealthy_savesLinkRecoveredAlert() {
        LinkStatusChanged e = event("BROKEN", "HEALTHY");
        when(alertLogRepository.existsByLinkIdAndAlertTypeAndFiredAtAfter(any(), eq("LINK_RECOVERED"), any()))
                .thenReturn(false);
        when(alertLogRepository.save(any())).thenReturn(savedLog(e, "LINK_RECOVERED"));

        alertService.processStatusChange(e);

        ArgumentCaptor<AlertLog> captor = ArgumentCaptor.forClass(AlertLog.class);
        verify(alertLogRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getAlertType()).isEqualTo("LINK_RECOVERED");
    }

    @Test
    void processStatusChange_timeoutToHealthy_savesLinkRecoveredAlert() {
        LinkStatusChanged e = event("TIMEOUT", "HEALTHY");
        when(alertLogRepository.existsByLinkIdAndAlertTypeAndFiredAtAfter(any(), eq("LINK_RECOVERED"), any()))
                .thenReturn(false);
        when(alertLogRepository.save(any())).thenReturn(savedLog(e, "LINK_RECOVERED"));

        alertService.processStatusChange(e);

        ArgumentCaptor<AlertLog> captor = ArgumentCaptor.forClass(AlertLog.class);
        verify(alertLogRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getAlertType()).isEqualTo("LINK_RECOVERED");
    }

    // --- no-alert scenarios ---

    @Test
    void processStatusChange_nullPreviousStatus_noAlertFired() {
        LinkStatusChanged e = event(null, "BROKEN");

        alertService.processStatusChange(e);

        verify(alertLogRepository, never()).existsByLinkIdAndAlertTypeAndFiredAtAfter(any(), any(), any());
        verify(alertLogRepository, never()).save(any());
    }

    @Test
    void processStatusChange_healthyToRedirected_noAlertFired() {
        LinkStatusChanged e = event("HEALTHY", "REDIRECTED");

        alertService.processStatusChange(e);

        verify(alertLogRepository, never()).save(any());
    }

    @Test
    void processStatusChange_redirectedToHealthy_noAlertFired() {
        // REDIRECTED is not a failure state, so recovery doesn't trigger LINK_RECOVERED
        LinkStatusChanged e = event("REDIRECTED", "HEALTHY");

        alertService.processStatusChange(e);

        verify(alertLogRepository, never()).save(any());
    }

    @Test
    void processStatusChange_brokenToBroken_noAlertFired() {
        // Status did not change — checker service wouldn't normally publish this,
        // but alert service should be defensive about it
        LinkStatusChanged e = event("BROKEN", "BROKEN");

        alertService.processStatusChange(e);

        verify(alertLogRepository, never()).save(any());
    }

    // --- deduplication ---

    @Test
    void processStatusChange_duplicateWithinOneHour_skipsAlert() {
        LinkStatusChanged e = event("HEALTHY", "BROKEN");
        when(alertLogRepository.existsByLinkIdAndAlertTypeAndFiredAtAfter(
                eq(e.getLinkId()), eq("LINK_BROKEN"), any()))
                .thenReturn(true);

        alertService.processStatusChange(e);

        verify(alertLogRepository, never()).save(any());
    }

    @Test
    void processStatusChange_deduplicationChecksCorrectLinkIdAndType() {
        LinkStatusChanged e = event("HEALTHY", "BROKEN");
        when(alertLogRepository.existsByLinkIdAndAlertTypeAndFiredAtAfter(any(), any(), any()))
                .thenReturn(false);
        when(alertLogRepository.save(any())).thenReturn(savedLog(e, "LINK_BROKEN"));

        alertService.processStatusChange(e);

        verify(alertLogRepository).existsByLinkIdAndAlertTypeAndFiredAtAfter(
                eq(e.getLinkId()), eq("LINK_BROKEN"), any());
    }

    // --- webhook delivery flag ---

    @Test
    void processStatusChange_afterWebhookFires_marksAlertDelivered() {
        LinkStatusChanged e = event("HEALTHY", "BROKEN");
        when(alertLogRepository.existsByLinkIdAndAlertTypeAndFiredAtAfter(any(), eq("LINK_BROKEN"), any()))
                .thenReturn(false);
        AlertLog log = savedLog(e, "LINK_BROKEN");
        when(alertLogRepository.save(any())).thenReturn(log);

        alertService.processStatusChange(e);

        // Second save should set delivered = true on the returned log instance
        ArgumentCaptor<AlertLog> captor = ArgumentCaptor.forClass(AlertLog.class);
        verify(alertLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getDelivered()).isTrue();
    }

    @Test
    void processStatusChange_savesAlertWithCorrectSiteAndLinkId() {
        LinkStatusChanged e = event("BROKEN", "HEALTHY");
        when(alertLogRepository.existsByLinkIdAndAlertTypeAndFiredAtAfter(any(), eq("LINK_RECOVERED"), any()))
                .thenReturn(false);
        AlertLog log = savedLog(e, "LINK_RECOVERED");
        when(alertLogRepository.save(any())).thenReturn(log);

        alertService.processStatusChange(e);

        ArgumentCaptor<AlertLog> captor = ArgumentCaptor.forClass(AlertLog.class);
        verify(alertLogRepository, atLeastOnce()).save(captor.capture());
        AlertLog firstSave = captor.getAllValues().get(0);
        assertThat(firstSave.getSiteId()).isEqualTo(e.getSiteId());
        assertThat(firstSave.getLinkId()).isEqualTo(e.getLinkId());
    }
}
