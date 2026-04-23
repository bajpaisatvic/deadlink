package com.example.alert_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LinkStatusChanged {
    private UUID linkId;
    private UUID siteId;
    private String url;
    private String previousStatus;
    private String newStatus;
    private Integer httpStatus;
    private LocalDateTime changedAt;
}