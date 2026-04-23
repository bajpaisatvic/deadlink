package com.example.alert_service.service;

import com.example.alert_service.dto.LinkStatusChanged;
import com.example.alert_service.model.AlertLog;
import com.example.alert_service.repository.AlertLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertLogRepository alertLogRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void processStatusChange(LinkStatusChanged event) {
        // Determine alert type
        String alertType = determineAlertType(event);
        if (alertType == null) return;

        // Deduplication — don't alert same link twice within 1 hour
        boolean alreadyAlerted = alertLogRepository
                .existsByLinkIdAndAlertTypeAndFiredAtAfter(
                        event.getLinkId(),
                        alertType,
                        LocalDateTime.now().minusHours(1)
                );

        if (alreadyAlerted) {
            log.info("Skipping duplicate alert for: {}", event.getUrl());
            return;
        }

        // Save alert log
        AlertLog alertLog = AlertLog.builder()
                .siteId(event.getSiteId())
                .linkId(event.getLinkId())
                .alertType(alertType)
                .delivered(false)
                .build();
        AlertLog saved = alertLogRepository.save(alertLog);

        // Fire webhook if URL provided
        log.info("Alert [{}] for: {}", alertType, event.getUrl());
        boolean delivered = fireWebhook(event);

        // Update delivered status
        saved.setDelivered(delivered);
        alertLogRepository.save(saved);
    }

    private String determineAlertType(LinkStatusChanged event) {
        String prev = event.getPreviousStatus();
        String curr = event.getNewStatus();

        if (prev == null) return null; // First time check — not an alert

        if (!prev.equals("BROKEN") && !prev.equals("TIMEOUT")
                && (curr.equals("BROKEN") || curr.equals("TIMEOUT"))) {
            return "LINK_BROKEN";
        }

        if ((prev.equals("BROKEN") || prev.equals("TIMEOUT"))
                && curr.equals("HEALTHY")) {
            return "LINK_RECOVERED";
        }

        return null;
    }

    private boolean fireWebhook(LinkStatusChanged event) {
        // In real life, fetch webhookUrl from crawler-service via REST
        // For now we log — webhook firing is wired up in a later task
        log.info("Would fire webhook for site: {} link: {} status: {}",
                event.getSiteId(), event.getUrl(), event.getNewStatus());
        return true;
    }
}