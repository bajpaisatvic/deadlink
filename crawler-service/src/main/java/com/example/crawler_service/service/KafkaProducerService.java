package com.example.crawler_service.service;

import com.example.crawler_service.dto.LinkCheckJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {
    private static final String TOPIC = "link-check-jobs";

    private final KafkaTemplate<String, LinkCheckJob> kafkaTemplate;

    public void publishLinkCheckJob(LinkCheckJob job){
        kafkaTemplate.send(TOPIC, job.getLinkId().toString(),job);
        log.info("Published link check job for: {}", job.getUrl());
    }
}
