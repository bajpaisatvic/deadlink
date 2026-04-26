package com.example.crawler_service.service;

import com.example.crawler_service.model.Site;
import com.example.crawler_service.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SiteSchedulingService {

    private final SiteRepository siteRepository;
    private final CrawlerService crawlerService;

    @Scheduled(fixedDelay = 60000)
    public void scheduleCrawls() {
        log.info("Scheduler running — checking for sites due for re-crawl...");

        List<Site> allSites = siteRepository.findAll();

        for (Site site : allSites) {
            if (isDueCrawl(site)){
                log.info("Site due for re-crawl: {}", site.getRootUrl());
                new Thread(() -> crawlerService.crawl(site)).start();
            }
        }
    }

    private boolean isDueCrawl(Site site) {

        if (site.getLastCrawledAt() == null)return true;
        LocalDateTime nextCrawlDue = site.getLastCrawledAt()
                .plusHours(site.getCheckInterval());

        return LocalDateTime.now().isAfter(nextCrawlDue);
    }
}
