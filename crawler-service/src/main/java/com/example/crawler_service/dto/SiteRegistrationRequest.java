package com.example.crawler_service.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class SiteRegistrationRequest {

    @NotBlank(message = "Site name is required")
    @Size(max = 255, message = "Name must be under 255 characters")
    private String name;

    @NotBlank(message = "Root URL is required")
    @Pattern(
            regexp = "^https?://.*",
            message = "Root URL must start with http:// or https://"
    )
    private String rootUrl;

    @Min(value = 1, message = "Crawl depth must be at least 1")
    @Max(value = 5, message = "Crawl depth cannot exceed 5")
    private Integer crawlDepth = 2;

    @Min(value = 1, message = "Check interval must be at least 1 hour")
    @Max(value = 720, message = "Check interval cannot exceed 720 hours (30 days)")
    private Integer checkIntervalHours = 24;

    private String webhookUrl;

    @Email(message = "Owner email must be a valid email address")
    private String ownerEmail;
}