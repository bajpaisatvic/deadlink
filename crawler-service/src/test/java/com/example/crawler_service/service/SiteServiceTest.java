package com.example.crawler_service.service;

import com.example.crawler_service.dto.SiteDetailsResponse;
import com.example.crawler_service.dto.SiteRegistrationRequest;
import com.example.crawler_service.dto.SiteRegistrationResponse;
import com.example.crawler_service.model.Site;
import com.example.crawler_service.repository.DiscoveredLinkRepository;
import com.example.crawler_service.repository.SiteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SiteServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private CrawlerService crawlerService;

    @Mock
    private DiscoveredLinkRepository discoveredLinkRepository;

    @InjectMocks
    private SiteService siteService;

    private SiteRegistrationRequest buildRequest() {
        SiteRegistrationRequest req = new SiteRegistrationRequest();
        req.setName("Test Site");
        req.setRootUrl("https://example.com");
        req.setCrawlDepth(2);
        req.setCheckIntervalHours(24);
        req.setWebhookUrl("https://hooks.example.com");
        req.setOwnerEmail("test@example.com");
        return req;
    }

    private Site buildSavedSite(UUID id) {
        return Site.builder()
                .id(id)
                .name("Test Site")
                .rootUrl("https://example.com")
                .crawlDepth(2)
                .checkInterval(24)
                .webhookUrl("https://hooks.example.com")
                .ownerEmail("test@example.com")
                .build();
    }

    @Test
    void registerSite_savesAndReturnsSiteId() {
        UUID siteId = UUID.randomUUID();
        Site savedSite = buildSavedSite(siteId);

        when(siteRepository.findByRootUrl("https://example.com")).thenReturn(Optional.empty());
        when(siteRepository.save(any(Site.class))).thenReturn(savedSite);

        SiteRegistrationResponse response = siteService.registerSite(buildRequest());

        assertThat(response.getSiteId()).isEqualTo(siteId);
        assertThat(response.getMessage()).contains("registered");
    }

    @Test
    void registerSite_persistsAllRequestFields() {
        Site savedSite = buildSavedSite(UUID.randomUUID());

        when(siteRepository.findByRootUrl(any())).thenReturn(Optional.empty());
        when(siteRepository.save(any(Site.class))).thenReturn(savedSite);

        siteService.registerSite(buildRequest());

        ArgumentCaptor<Site> captor = ArgumentCaptor.forClass(Site.class);
        verify(siteRepository).save(captor.capture());

        Site persisted = captor.getValue();
        assertThat(persisted.getName()).isEqualTo("Test Site");
        assertThat(persisted.getRootUrl()).isEqualTo("https://example.com");
        assertThat(persisted.getCrawlDepth()).isEqualTo(2);
        assertThat(persisted.getCheckInterval()).isEqualTo(24);
        assertThat(persisted.getWebhookUrl()).isEqualTo("https://hooks.example.com");
        assertThat(persisted.getOwnerEmail()).isEqualTo("test@example.com");
    }

    @Test
    void registerSite_startsAsyncCrawlAfterSaving() {
        Site savedSite = buildSavedSite(UUID.randomUUID());

        when(siteRepository.findByRootUrl(any())).thenReturn(Optional.empty());
        when(siteRepository.save(any())).thenReturn(savedSite);

        siteService.registerSite(buildRequest());

        // Crawl is kicked off in a new thread — allow time for it to invoke the mock
        verify(crawlerService, timeout(1000)).crawl(savedSite);
    }

    @Test
    void registerSite_throwsWhenUrlAlreadyRegistered() {
        when(siteRepository.findByRootUrl("https://example.com"))
                .thenReturn(Optional.of(new Site()));

        assertThatThrownBy(() -> siteService.registerSite(buildRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https://example.com");
    }

    @Test
    void registerSite_doesNotSaveOrCrawlWhenDuplicate() {
        when(siteRepository.findByRootUrl(any())).thenReturn(Optional.of(new Site()));

        assertThatThrownBy(() -> siteService.registerSite(buildRequest()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(siteRepository, never()).save(any());
        verify(crawlerService, never()).crawl(any());
    }

    @Test
    void getSiteDetails_returnsFullResponse() {
        UUID siteId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 15, 10, 0);
        LocalDateTime lastCrawledAt = LocalDateTime.of(2024, 1, 15, 12, 0);

        Site site = Site.builder()
                .id(siteId)
                .name("Test Site")
                .rootUrl("https://example.com")
                .crawlDepth(3)
                .checkInterval(12)
                .createdAt(createdAt)
                .lastCrawledAt(lastCrawledAt)
                .build();

        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
        when(discoveredLinkRepository.countBySiteId(siteId)).thenReturn(42L);

        SiteDetailsResponse response = siteService.getSiteDetails(siteId);

        assertThat(response.getSiteId()).isEqualTo(siteId);
        assertThat(response.getName()).isEqualTo("Test Site");
        assertThat(response.getRootUrl()).isEqualTo("https://example.com");
        assertThat(response.getCrawlDepth()).isEqualTo(3);
        assertThat(response.getCheckIntervalHours()).isEqualTo(12);
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        assertThat(response.getLastCrawledAt()).isEqualTo(lastCrawledAt);
        assertThat(response.getTotalLinksDiscovered()).isEqualTo(42L);
    }

    @Test
    void getSiteDetails_returnsZeroLinksWhenNoneDiscovered() {
        UUID siteId = UUID.randomUUID();
        Site site = buildSavedSite(siteId);

        when(siteRepository.findById(siteId)).thenReturn(Optional.of(site));
        when(discoveredLinkRepository.countBySiteId(siteId)).thenReturn(0L);

        SiteDetailsResponse response = siteService.getSiteDetails(siteId);

        assertThat(response.getTotalLinksDiscovered()).isZero();
    }

    @Test
    void getSiteDetails_throwsWhenSiteNotFound() {
        UUID siteId = UUID.randomUUID();
        when(siteRepository.findById(siteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> siteService.getSiteDetails(siteId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(siteId.toString());
    }

    @Test
    void getSiteDetails_throwsDoesNotQueryLinksWhenSiteAbsent() {
        UUID siteId = UUID.randomUUID();
        when(siteRepository.findById(siteId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> siteService.getSiteDetails(siteId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(discoveredLinkRepository, never()).countBySiteId(any());
    }
}
