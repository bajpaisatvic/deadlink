package com.example.crawler_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LinkCheckJob {
    private UUID linkId;
    private UUID siteId;
    private String url;
}
