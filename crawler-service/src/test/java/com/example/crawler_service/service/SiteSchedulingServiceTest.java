package com.example.crawler_service.service;

import com.example.crawler_service.model.Site;
import com.example.crawler_service.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SiteSchedulingServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private CrawlerService crawlerService;

    @InjectMocks
    private SiteSchedulingService siteSchedulingService;

    private Site buildSite(LocalDateTime lastCrawledAt, int checkIntervalHours) {
        return Site.builder()
                .id(UUID.randomUUID())
                .name("Test Site")
                .rootUrl("https://example.com")
                .crawlDepth(2)
                .checkInterval(checkIntervalHours)
                .lastCrawledAt(lastCrawledAt)
                .build();
    }

    @Test
    void scheduleCrawls_triggersCrawlForSiteNeverCrawled() {
        Site site = buildSite(null, 24);
        when(siteRepository.findAll()).thenReturn(List.of(site));

        siteSchedulingService.scheduleCrawls();

        // Crawl runs in a new thread — timeout allows it time to start
        verify(crawlerService, timeout(1000)).crawl(site);
    }

    @Test
    void scheduleCrawls_triggersCrawlForOverdueSite() {
        // Last crawled 25h ago with a 24h interval → overdue
        Site site = buildSite(LocalDateTime.now().minusHours(25), 24);
        when(siteRepository.findAll()).thenReturn(List.of(site));

        siteSchedulingService.scheduleCrawls();

        verify(crawlerService, timeout(1000)).crawl(site);
    }

    @Test
    void scheduleCrawls_doesNotTriggerCrawlForUpToDateSite() {
        // Last crawled 1h ago with a 24h interval → not yet due
        Site site = buildSite(LocalDateTime.now().minusHours(1), 24);
        when(siteRepository.findAll()).thenReturn(List.of(site));

        siteSchedulingService.scheduleCrawls();

        verify(crawlerService, never()).crawl(any());
    }

    @Test
    void scheduleCrawls_handlesEmptySiteList() {
        when(siteRepository.findAll()).thenReturn(List.of());

        siteSchedulingService.scheduleCrawls();

        verify(crawlerService, never()).crawl(any());
    }

    @Test
    void scheduleCrawls_triggersCrawlJustAfterIntervalExpires() {
        // Last crawled exactly checkInterval + 1 minute ago → should be due
        Site site = buildSite(LocalDateTime.now().minusHours(24).minusMinutes(1), 24);
        when(siteRepository.findAll()).thenReturn(List.of(site));

        siteSchedulingService.scheduleCrawls();

        verify(crawlerService, timeout(1000)).crawl(site);
    }

    @Test
    void scheduleCrawls_doesNotTriggerCrawlJustBeforeIntervalExpires() {
        // Last crawled 23h 59m ago with a 24h interval → not yet due
        Site site = buildSite(LocalDateTime.now().minusHours(23).minusMinutes(59), 24);
        when(siteRepository.findAll()).thenReturn(List.of(site));

        siteSchedulingService.scheduleCrawls();

        verify(crawlerService, never()).crawl(any());
    }

    @Test
    void scheduleCrawls_handlesMultipleSitesSelectively() {
        Site dueSite = buildSite(LocalDateTime.now().minusHours(30), 24);
        Site notDueSite = buildSite(LocalDateTime.now().minusHours(1), 24);
        Site neverCrawledSite = buildSite(null, 24);

        when(siteRepository.findAll()).thenReturn(List.of(dueSite, notDueSite, neverCrawledSite));

        siteSchedulingService.scheduleCrawls();

        verify(crawlerService, timeout(1000)).crawl(dueSite);
        verify(crawlerService, timeout(1000)).crawl(neverCrawledSite);
        verify(crawlerService, never()).crawl(notDueSite);
    }

    @Test
    void scheduleCrawls_respectsCustomCheckInterval() {
        // Site with a 6h interval, last crawled 7h ago → due
        Site dueSite = buildSite(LocalDateTime.now().minusHours(7), 6);
        // Site with a 48h interval, last crawled 30h ago → not due
        Site notDueSite = buildSite(LocalDateTime.now().minusHours(30), 48);

        when(siteRepository.findAll()).thenReturn(List.of(dueSite, notDueSite));

        siteSchedulingService.scheduleCrawls();

        verify(crawlerService, timeout(1000)).crawl(dueSite);
        verify(crawlerService, never()).crawl(notDueSite);
    }
}
