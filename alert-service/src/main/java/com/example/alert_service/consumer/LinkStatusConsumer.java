package com.example.alert_service.consumer;

import com.example.alert_service.dto.LinkStatusChanged;
import com.example.alert_service.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LinkStatusConsumer {

    private final AlertService alertService;

    @KafkaListener(
            topics = "link-status-changed",
            groupId = "alert-service-group"
    )
    public void consume(LinkStatusChanged event) {
        log.info("Received status change: {} → {} for {}",
                event.getPreviousStatus(), event.getNewStatus(), event.getUrl());
        alertService.processStatusChange(event);
    }
}