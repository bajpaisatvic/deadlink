package com.example.alert_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class BrokenLinkDto {
    private UUID linkId;
    private String alertType;
    private LocalDateTime firedAt;
    private Boolean delivered;
}