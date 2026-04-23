package com.example.crawler_service.service;

import com.example.crawler_service.dto.LinkCheckJob;
import com.example.crawler_service.model.DiscoveredLink;
import com.example.crawler_service.model.Site;
import com.example.crawler_service.repository.DiscoveredLinkRepository;
import com.example.crawler_service.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlerService {

    private final DiscoveredLinkRepository discoveredLinkRepository;
    private final SiteRepository siteRepository;
    private final KafkaProducerService kafkaProducerService;
    public void crawl(Site site) {
        log.info("Starting crawl for site: {}", site.getRootUrl());

        String rootUrl = site.getRootUrl();
        int maxDepth = site.getCrawlDepth();

        Queue<String[]> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.add(new String[]{rootUrl, "0"});
        visited.add(rootUrl);

        while (!queue.isEmpty()) {
            String[] current = queue.poll();
            String currentUrl = current[0];
            int currentDepth = Integer.parseInt(current[1]);

            if (currentDepth > maxDepth) continue;

            log.info("Crawling [depth={}]: {}", currentDepth, currentUrl);

            try {
                Document doc = Jsoup.connect(currentUrl)
                        .userAgent("DeadLink-Bot/1.0")
                        .timeout(8000)
                        .get();

                // Extract all <a href> links from the page
                doc.select("a[href]").forEach(link -> {
                    String absUrl = link.absUrl("href");

                    // Only process valid http/https links
                    if (absUrl.isEmpty() || (!absUrl.startsWith("http://") && !absUrl.startsWith("https://"))) {
                        return;
                    }

                    // Remove fragments (#section) — same page
                    if (absUrl.contains("#")) {
                        absUrl = absUrl.substring(0, absUrl.indexOf("#"));
                    }

                    // Save every discovered link regardless of domain
                    if (!discoveredLinkRepository.existsBySiteIdAndUrl(site.getId(), absUrl)) {
                        DiscoveredLink dl = DiscoveredLink.builder()
                                .site(site)
                                .url(absUrl)
                                .foundOn(currentUrl)
                                .build();
                        DiscoveredLink saved = discoveredLinkRepository.save(dl);
                        log.info("Discovered: {}", absUrl);
                        kafkaProducerService.publishLinkCheckJob(
                                new LinkCheckJob(saved.getId(),site.getId(),absUrl)
                        );

                    }

                    // Only follow same-domain links for deeper crawling
                    String rootDomain = extractDomain(rootUrl);
                    String linkDomain = extractDomain(absUrl);

                    if (rootDomain.equals(linkDomain) && !visited.contains(absUrl) && currentDepth < maxDepth) {
                        visited.add(absUrl);
                        queue.add(new String[]{absUrl, String.valueOf(currentDepth + 1)});
                    }
                });

                // Polite crawling — don't hammer the server
                Thread.sleep(500);

            } catch (IOException e) {
                log.warn("Failed to fetch {}: {}", currentUrl, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("Crawl complete for site: {}. Updating lastCrawledAt.", site.getRootUrl());
        site.setLastCrawledAt(java.time.LocalDateTime.now());
        siteRepository.save(site);
    }

    private String extractDomain(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String host = uri.getHost();
            return host != null ? host.toLowerCase() : "";
        } catch (Exception e) {
            return "";
        }
    }
}