package com.example.crawler_service.service;

import com.example.crawler_service.dto.LinkCheckJob;
import com.example.crawler_service.model.DiscoveredLink;
import com.example.crawler_service.model.Site;
import com.example.crawler_service.repository.DiscoveredLinkRepository;
import com.example.crawler_service.repository.SiteRepository;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CrawlerServiceTest {

    @Mock
    private DiscoveredLinkRepository discoveredLinkRepository;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @InjectMocks
    private CrawlerService crawlerService;

    private Site buildSite(int crawlDepth) {
        return Site.builder()
                .id(UUID.randomUUID())
                .name("Test Site")
                .rootUrl("https://example.com")
                .crawlDepth(crawlDepth)
                .checkInterval(24)
                .build();
    }

    private Connection buildMockConnection(Document doc) throws IOException {
        Connection conn = mock(Connection.class);
        when(conn.userAgent(anyString())).thenReturn(conn);
        when(conn.timeout(anyInt())).thenReturn(conn);
        when(conn.get()).thenReturn(doc);
        return conn;
    }

    private DiscoveredLink stubSavedLink(Site site, String url) {
        return DiscoveredLink.builder()
                .id(UUID.randomUUID())
                .site(site)
                .url(url)
                .foundOn(site.getRootUrl())
                .build();
    }

    @Test
    void crawl_savesDiscoveredLinksAndPublishesKafkaJobs() throws IOException {
        Site site = buildSite(0);
        Document rootDoc = Jsoup.parse(
                "<a href='/page1'>Page1</a><a href='https://external.com'>External</a>",
                "https://example.com");
        Connection conn = buildMockConnection(rootDoc);

        when(discoveredLinkRepository.existsBySiteIdAndUrl(any(), anyString())).thenReturn(false);
        when(discoveredLinkRepository.save(any())).thenAnswer(inv -> {
            DiscoveredLink dl = inv.getArgument(0);
            return stubSavedLink(site, dl.getUrl());
        });

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(conn);
            crawlerService.crawl(site);
        }

        verify(discoveredLinkRepository, times(2)).save(any(DiscoveredLink.class));
        verify(kafkaProducerService, times(2)).publishLinkCheckJob(any(LinkCheckJob.class));
        verify(siteRepository).save(site);
        assertThat(site.getLastCrawledAt()).isNotNull();
    }

    @Test
    void crawl_skipsAlreadyDiscoveredLinks() throws IOException {
        Site site = buildSite(0);
        Document rootDoc = Jsoup.parse("<a href='/page1'>Page1</a>", "https://example.com");
        Connection conn = buildMockConnection(rootDoc);

        when(discoveredLinkRepository.existsBySiteIdAndUrl(any(), anyString())).thenReturn(true);

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(conn);
            crawlerService.crawl(site);
        }

        verify(discoveredLinkRepository, never()).save(any());
        verify(kafkaProducerService, never()).publishLinkCheckJob(any());
    }

    @Test
    void crawl_stripsFragmentsFromUrls() throws IOException {
        Site site = buildSite(0);
        Document rootDoc = Jsoup.parse("<a href='/page1#section'>Link</a>", "https://example.com");
        Connection conn = buildMockConnection(rootDoc);

        when(discoveredLinkRepository.existsBySiteIdAndUrl(any(), anyString())).thenReturn(false);
        when(discoveredLinkRepository.save(any())).thenAnswer(inv -> {
            DiscoveredLink dl = inv.getArgument(0);
            return stubSavedLink(site, dl.getUrl());
        });

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(conn);
            crawlerService.crawl(site);
        }

        ArgumentCaptor<DiscoveredLink> captor = ArgumentCaptor.forClass(DiscoveredLink.class);
        verify(discoveredLinkRepository).save(captor.capture());
        assertThat(captor.getValue().getUrl())
                .isEqualTo("https://example.com/page1")
                .doesNotContain("#");
    }

    @Test
    void crawl_skipsNonHttpLinks() throws IOException {
        Site site = buildSite(0);
        // Jsoup returns "" for absUrl on non-http schemes (javascript:) and
        // returns the raw URI for mailto: which doesn't start with http(s)
        Document rootDoc = Jsoup.parse(
                "<a href='mailto:foo@bar.com'>Email</a><a href='javascript:void(0)'>JS</a>",
                "https://example.com");
        Connection conn = buildMockConnection(rootDoc);

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(conn);
            crawlerService.crawl(site);
        }

        verify(discoveredLinkRepository, never()).save(any());
        verify(kafkaProducerService, never()).publishLinkCheckJob(any());
    }

    @Test
    void crawl_savesExternalLinksButDoesNotFollowThem() throws IOException {
        Site site = buildSite(1);

        Document rootDoc = Jsoup.parse(
                "<a href='/internal'>Internal</a><a href='https://other.com/page'>External</a>",
                "https://example.com");
        Document internalDoc = Jsoup.parse("", "https://example.com/internal");

        Connection rootConn = buildMockConnection(rootDoc);
        Connection internalConn = buildMockConnection(internalDoc);

        when(discoveredLinkRepository.existsBySiteIdAndUrl(any(), anyString())).thenReturn(false);
        when(discoveredLinkRepository.save(any())).thenAnswer(inv -> {
            DiscoveredLink dl = inv.getArgument(0);
            return stubSavedLink(site, dl.getUrl());
        });

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenAnswer(invocation -> {
                String url = invocation.getArgument(0);
                if ("https://example.com".equals(url)) return rootConn;
                if ("https://example.com/internal".equals(url)) return internalConn;
                throw new AssertionError("Unexpected Jsoup.connect call for URL: " + url);
            });

            crawlerService.crawl(site);
        }

        // Internal page was fetched (BFS followed it)
        verify(internalConn).get();

        // Both internal and external links are saved as discovered
        ArgumentCaptor<DiscoveredLink> captor = ArgumentCaptor.forClass(DiscoveredLink.class);
        verify(discoveredLinkRepository, times(2)).save(captor.capture());
        List<String> savedUrls = captor.getAllValues().stream()
                .map(DiscoveredLink::getUrl).toList();
        assertThat(savedUrls).contains("https://example.com/internal", "https://other.com/page");
    }

    @Test
    void crawl_respectsDepthLimit() throws IOException {
        // crawlDepth=1: root (depth 0) and page1 (depth 1) are fetched,
        // but page2 (which would be depth 2) must not be fetched
        Site site = buildSite(1);

        Document rootDoc = Jsoup.parse("<a href='/page1'>Page1</a>", "https://example.com");
        Document page1Doc = Jsoup.parse("<a href='/page2'>Page2</a>", "https://example.com/page1");

        Connection rootConn = buildMockConnection(rootDoc);
        Connection page1Conn = buildMockConnection(page1Doc);

        when(discoveredLinkRepository.existsBySiteIdAndUrl(any(), anyString())).thenReturn(false);
        when(discoveredLinkRepository.save(any())).thenAnswer(inv -> {
            DiscoveredLink dl = inv.getArgument(0);
            return stubSavedLink(site, dl.getUrl());
        });

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenAnswer(invocation -> {
                String url = invocation.getArgument(0);
                if ("https://example.com".equals(url)) return rootConn;
                if ("https://example.com/page1".equals(url)) return page1Conn;
                throw new AssertionError("Unexpected Jsoup.connect call for URL: " + url);
            });

            crawlerService.crawl(site);
        }

        // Root and page1 were fetched
        verify(rootConn).get();
        verify(page1Conn).get();

        // page2 was discovered on page1 but queuing is blocked (depth 1 < maxDepth 1 is false)
        ArgumentCaptor<DiscoveredLink> captor = ArgumentCaptor.forClass(DiscoveredLink.class);
        verify(discoveredLinkRepository, times(2)).save(captor.capture());
        List<String> savedUrls = captor.getAllValues().stream()
                .map(DiscoveredLink::getUrl).toList();
        assertThat(savedUrls).contains("https://example.com/page1", "https://example.com/page2");
    }

    @Test
    void crawl_handlesIOExceptionGracefullyAndStillUpdatesLastCrawledAt() throws IOException {
        Site site = buildSite(0);
        Connection conn = mock(Connection.class);
        when(conn.userAgent(anyString())).thenReturn(conn);
        when(conn.timeout(anyInt())).thenReturn(conn);
        when(conn.get()).thenThrow(new IOException("connection refused"));

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(conn);
            crawlerService.crawl(site);
        }

        verify(discoveredLinkRepository, never()).save(any());
        verify(kafkaProducerService, never()).publishLinkCheckJob(any());
        verify(siteRepository).save(site);
        assertThat(site.getLastCrawledAt()).isNotNull();
    }

    @Test
    void crawl_updatesLastCrawledAtAfterCompletion() throws IOException {
        Site site = buildSite(0);
        Document rootDoc = Jsoup.parse("", "https://example.com");
        Connection conn = buildMockConnection(rootDoc);

        LocalDateTime before = LocalDateTime.now();

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(conn);
            crawlerService.crawl(site);
        }

        assertThat(site.getLastCrawledAt()).isAfterOrEqualTo(before);
        verify(siteRepository).save(site);
    }

    @Test
    void crawl_publishesCorrectLinkCheckJobPayload() throws IOException {
        Site site = buildSite(0);
        Document rootDoc = Jsoup.parse("<a href='/page1'>Page1</a>", "https://example.com");
        Connection conn = buildMockConnection(rootDoc);

        UUID linkId = UUID.randomUUID();
        DiscoveredLink savedLink = DiscoveredLink.builder()
                .id(linkId)
                .site(site)
                .url("https://example.com/page1")
                .foundOn("https://example.com")
                .build();

        when(discoveredLinkRepository.existsBySiteIdAndUrl(any(), anyString())).thenReturn(false);
        when(discoveredLinkRepository.save(any())).thenReturn(savedLink);

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenReturn(conn);
            crawlerService.crawl(site);
        }

        ArgumentCaptor<LinkCheckJob> jobCaptor = ArgumentCaptor.forClass(LinkCheckJob.class);
        verify(kafkaProducerService).publishLinkCheckJob(jobCaptor.capture());

        LinkCheckJob job = jobCaptor.getValue();
        assertThat(job.getLinkId()).isEqualTo(linkId);
        assertThat(job.getSiteId()).isEqualTo(site.getId());
        assertThat(job.getUrl()).isEqualTo("https://example.com/page1");
    }

    @Test
    void crawl_doesNotFollowAlreadyVisitedUrls() throws IOException {
        // Two links to the same page should result in it only being queued once
        Site site = buildSite(1);
        Document rootDoc = Jsoup.parse(
                "<a href='/page1'>Link A</a><a href='/page1'>Link B (duplicate)</a>",
                "https://example.com");
        Document page1Doc = Jsoup.parse("", "https://example.com/page1");

        Connection rootConn = buildMockConnection(rootDoc);
        Connection page1Conn = buildMockConnection(page1Doc);

        when(discoveredLinkRepository.existsBySiteIdAndUrl(any(), anyString()))
                .thenReturn(false)  // first check for /page1
                .thenReturn(true);  // second check (duplicate link on same page)
        when(discoveredLinkRepository.save(any())).thenAnswer(inv -> {
            DiscoveredLink dl = inv.getArgument(0);
            return stubSavedLink(site, dl.getUrl());
        });

        try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
            jsoupMock.when(() -> Jsoup.connect(anyString())).thenAnswer(invocation -> {
                String url = invocation.getArgument(0);
                if ("https://example.com".equals(url)) return rootConn;
                if ("https://example.com/page1".equals(url)) return page1Conn;
                throw new AssertionError("Unexpected URL: " + url);
            });

            crawlerService.crawl(site);
        }

        // /page1 fetched exactly once even though it appeared twice in the root doc
        verify(page1Conn, times(1)).get();
    }
}
