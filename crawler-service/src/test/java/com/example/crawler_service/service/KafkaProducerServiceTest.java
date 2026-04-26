package com.example.crawler_service.service;

import com.example.crawler_service.dto.LinkCheckJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class KafkaProducerServiceTest {

    @Mock
    private KafkaTemplate<String, LinkCheckJob> kafkaTemplate;

    @InjectMocks
    private KafkaProducerService kafkaProducerService;

    @Test
    void publishLinkCheckJob_sendsToCorrectTopicWithLinkIdAsKey() {
        UUID linkId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        LinkCheckJob job = new LinkCheckJob(linkId, siteId, "https://example.com/page");

        kafkaProducerService.publishLinkCheckJob(job);

        verify(kafkaTemplate).send("link-check-jobs", linkId.toString(), job);
    }

    @Test
    void publishLinkCheckJob_usesLinkIdAsMessageKey() {
        UUID linkId = UUID.randomUUID();
        LinkCheckJob job = new LinkCheckJob(linkId, UUID.randomUUID(), "https://example.com");

        kafkaProducerService.publishLinkCheckJob(job);

        // Key must be linkId.toString() — enables partition-level ordering per link
        verify(kafkaTemplate).send("link-check-jobs", linkId.toString(), job);
    }

    @Test
    void publishLinkCheckJob_passesJobAsValueUnmodified() {
        UUID linkId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        String url = "https://docs.example.com/some-deep/path";
        LinkCheckJob job = new LinkCheckJob(linkId, siteId, url);

        kafkaProducerService.publishLinkCheckJob(job);

        verify(kafkaTemplate).send("link-check-jobs", linkId.toString(), job);
    }
}
