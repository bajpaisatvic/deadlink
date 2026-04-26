package com.example.alert_service.consumer;

import com.example.alert_service.dto.LinkStatusChanged;
import com.example.alert_service.service.AlertService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class LinkStatusConsumerTest {

    @Mock private AlertService alertService;
    @InjectMocks private LinkStatusConsumer linkStatusConsumer;

    @Test
    void consume_delegatesToAlertService() {
        LinkStatusChanged event = new LinkStatusChanged(
                UUID.randomUUID(), UUID.randomUUID(),
                "https://example.com/page",
                "HEALTHY", "BROKEN", 404, LocalDateTime.now()
        );

        linkStatusConsumer.consume(event);

        verify(alertService).processStatusChange(event);
    }

    @Test
    void consume_passesExactEventInstance() {
        LinkStatusChanged event = new LinkStatusChanged(
                UUID.randomUUID(), UUID.randomUUID(),
                "https://docs.example.com/guide",
                null, "HEALTHY", 200, LocalDateTime.now()
        );

        linkStatusConsumer.consume(event);

        verify(alertService).processStatusChange(event);
    }
}
