package com.example.crawler_service.service;

import com.example.crawler_service.dto.SiteDetailsResponse;
import com.example.crawler_service.dto.SiteRegistrationRequest;
import com.example.crawler_service.dto.SiteRegistrationResponse;
import com.example.crawler_service.model.Site;
import com.example.crawler_service.repository.DiscoveredLinkRepository;
import com.example.crawler_service.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteService {

    private final SiteRepository siteRepository;
    private final CrawlerService crawlerService;
    private final DiscoveredLinkRepository  discoveredLinkRepository;

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

    public SiteDetailsResponse getSiteDetails(UUID siteId) {
        Site site = siteRepository.findById(siteId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Site not found: " + siteId));

        long totalLinks = discoveredLinkRepository.countBySiteId(siteId);

        return new SiteDetailsResponse(
                site.getId(),
                site.getName(),
                site.getRootUrl(),
                site.getCrawlDepth(),
                site.getCheckInterval(),
                site.getCreatedAt(),
                site.getLastCrawledAt(),
                totalLinks
        );
    }
}