package com.example.checker_service.service;

import com.example.checker_service.dto.LinkStatusChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private static final String TOPIC = "link-status-changed";

    private final KafkaTemplate<String, LinkStatusChanged> kafkaTemplate;

    public void publishStatusChanged(LinkStatusChanged event) {
        kafkaTemplate.send(TOPIC, event.getLinkId().toString(), event);
        log.info("Published status change: {} → {} for {}",
                event.getPreviousStatus(), event.getNewStatus(), event.getUrl());
    }
}