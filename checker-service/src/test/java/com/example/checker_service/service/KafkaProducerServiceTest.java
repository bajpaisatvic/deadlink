package com.example.checker_service.service;

import com.example.checker_service.dto.LinkStatusChanged;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class KafkaProducerServiceTest {

    @Mock private KafkaTemplate<String, LinkStatusChanged> kafkaTemplate;
    @InjectMocks private KafkaProducerService kafkaProducerService;

    private LinkStatusChanged buildEvent() {
        return new LinkStatusChanged(
                UUID.randomUUID(), UUID.randomUUID(),
                "https://example.com/page",
                "HEALTHY", "BROKEN", 404, LocalDateTime.now()
        );
    }

    @Test
    void publishStatusChanged_sendsToCorrectTopicWithCorrectKeyAndPayload() {
        LinkStatusChanged event = buildEvent();

        kafkaProducerService.publishStatusChanged(event);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LinkStatusChanged> payloadCaptor = ArgumentCaptor.forClass(LinkStatusChanged.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo("link-status-changed");
        assertThat(keyCaptor.getValue()).isEqualTo(event.getLinkId().toString());
        assertThat(payloadCaptor.getValue()).isSameAs(event);
    }

    @Test
    void publishStatusChanged_calledOncePerEvent() {
        LinkStatusChanged event = buildEvent();

        kafkaProducerService.publishStatusChanged(event);

        verify(kafkaTemplate, org.mockito.Mockito.times(1))
                .send(org.mockito.ArgumentMatchers.anyString(),
                      org.mockito.ArgumentMatchers.anyString(),
                      org.mockito.ArgumentMatchers.any());
    }
}
