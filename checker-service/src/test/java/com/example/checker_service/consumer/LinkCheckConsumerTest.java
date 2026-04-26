package com.example.checker_service.consumer;

import com.example.checker_service.dto.LinkCheckJob;
import com.example.checker_service.dto.LinkStatusChanged;
import com.example.checker_service.service.KafkaProducerService;
import com.example.checker_service.service.LinkCheckerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LinkCheckConsumerTest {

    @Mock private LinkCheckerService linkCheckerService;
    @Mock private KafkaProducerService kafkaProducerService;
    @InjectMocks private LinkCheckConsumer linkCheckConsumer;

    private LinkCheckJob job() {
        return new LinkCheckJob(UUID.randomUUID(), UUID.randomUUID(), "https://example.com");
    }

    @Test
    void consume_whenStatusChanged_publishesEvent() {
        LinkCheckJob job = job();
        LinkStatusChanged event = new LinkStatusChanged(
                job.getLinkId(), job.getSiteId(), job.getUrl(),
                "HEALTHY", "BROKEN", 404, LocalDateTime.now()
        );
        when(linkCheckerService.check(job)).thenReturn(Optional.of(event));

        linkCheckConsumer.consume(job);

        verify(kafkaProducerService).publishStatusChanged(event);
    }

    @Test
    void consume_whenStatusUnchanged_doesNotPublish() {
        LinkCheckJob job = job();
        when(linkCheckerService.check(job)).thenReturn(Optional.empty());

        linkCheckConsumer.consume(job);

        verify(kafkaProducerService, never()).publishStatusChanged(any());
    }

    @Test
    void consume_alwaysDelegatesCheckToLinkCheckerService() {
        LinkCheckJob job = job();
        when(linkCheckerService.check(job)).thenReturn(Optional.empty());

        linkCheckConsumer.consume(job);

        verify(linkCheckerService).check(job);
    }
}
