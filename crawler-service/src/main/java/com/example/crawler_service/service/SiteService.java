package com.example.crawler_service.service;

import com.example.crawler_service.dto.SiteRegistrationRequest;
import com.example.crawler_service.dto.SiteRegistrationResponse;
import com.example.crawler_service.model.Site;
import com.example.crawler_service.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteService {

    private final SiteRepository siteRepository;
    private final CrawlerService crawlerService;

    public SiteRegistrationResponse registerSite(SiteRegistrationRequest request) {

        if (siteRepository.findByRootUrl(request.getRootUrl()).isPresent()) {
            throw new IllegalArgumentException(
                    "Site already registered: " + request.getRootUrl()
            );
        }

        Site site = Site.builder()
                .name(request.getName())
                .rootUrl(request.getRootUrl())
                .crawlDepth(request.getCrawlDepth())
                .checkInterval(request.getCheckIntervalHours())
                .webhookUrl(request.getWebhookUrl())
                .ownerEmail(request.getOwnerEmail())
                .build();

        Site saved = siteRepository.save(site);
        log.info("Registered new site: {} with id: {}", saved.getName(), saved.getId());

        new Thread(() -> crawlerService.crawl(saved)).start();

        return new SiteRegistrationResponse(
                saved.getId(),
                "Site registered successfully. Crawl will start shortly."
        );
    }
}