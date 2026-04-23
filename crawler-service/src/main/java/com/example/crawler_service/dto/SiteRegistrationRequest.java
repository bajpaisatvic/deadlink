package com.example.crawler_service.dto;

import lombok.Data;

@Data
public class SiteRegistrationRequest {
    private String name;
    private String rootUrl;
    private Integer crawlDepth = 2;
    private Integer checkIntervalHours = 24;
    private String webhookUrl;
    private String ownerEmail;
}
