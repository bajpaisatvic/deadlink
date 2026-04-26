package com.example.crawler_service.controller;

import com.example.crawler_service.dto.SiteDetailsResponse;
import com.example.crawler_service.dto.SiteRegistrationRequest;
import com.example.crawler_service.dto.SiteRegistrationResponse;
import com.example.crawler_service.service.SiteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    @PostMapping
    public ResponseEntity<SiteRegistrationResponse> registerSite(
            @Valid @RequestBody SiteRegistrationRequest request) {
        SiteRegistrationResponse response = siteService.registerSite(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{siteId}")
    public ResponseEntity<SiteDetailsResponse> getSiteDetails(@PathVariable UUID siteId) {
        return ResponseEntity.ok(siteService.getSiteDetails(siteId));
    }

    @PostMapping("/{siteId}/crawl")
    public ResponseEntity<Map<String, String>> triggerCrawl(@PathVariable UUID siteId) {
        siteService.triggerCrawl(siteId);
        return ResponseEntity.accepted()
                .body(Map.of("message", "Crawl triggered successfully."));
    }

    @GetMapping
    public ResponseEntity<List<SiteDetailsResponse>> getAllSites() {
        return ResponseEntity.ok(siteService.getAllSites());
    }
}