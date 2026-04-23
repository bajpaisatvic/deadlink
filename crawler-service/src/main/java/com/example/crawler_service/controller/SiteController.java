package com.example.crawler_service.controller;

import com.example.crawler_service.dto.SiteRegistrationRequest;
import com.example.crawler_service.dto.SiteRegistrationResponse;
import com.example.crawler_service.service.SiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sites")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;

    @PostMapping
    public ResponseEntity<SiteRegistrationResponse> registerSite(
            @RequestBody SiteRegistrationRequest request) {
        SiteRegistrationResponse response = siteService.registerSite(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}