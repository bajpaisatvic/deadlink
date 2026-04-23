package com.example.crawler_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class SiteRegistrationResponse {
    private UUID siteId;
    private String message;
}
