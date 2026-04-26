package com.example.crawler_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class SiteDetailsResponse {
    private UUID siteId;
    private String name;
    private String rootUrl;
    private Integer crawlDepth;
    private Integer checkIntervalHours;
    private LocalDateTime createdAt;
    private LocalDateTime lastCrawledAt;
    private Long totalLinksDiscovered;
}