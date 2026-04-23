package com.example.checker_service.consumer;

import com.example.checker_service.dto.LinkCheckJob;
import com.example.checker_service.service.KafkaProducerService;
import com.example.checker_service.service.LinkCheckerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LinkCheckConsumer {

    private final LinkCheckerService linkCheckerService;
    private final KafkaProducerService kafkaProducerService;

    @KafkaListener(
            topics = "link-check-jobs",
            groupId = "checker-service-group"
    )
    public void consume(LinkCheckJob job) {
        log.info("Received link check job for: {}", job.getUrl());
        linkCheckerService.check(job)
                .ifPresent(kafkaProducerService::publishStatusChanged);
    }
}