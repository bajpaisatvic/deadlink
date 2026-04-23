package com.example.crawler_service.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sites", schema = "crawler")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "root_url", nullable = false)
    private String rootUrl;

    @Column(name = "crawl_depth", nullable = false)
    private Integer crawlDepth = 2;

    @Column(name = "check_interval", nullable = false)
    private Integer checkInterval = 24;

    @Column(name = "webhook_url")
    private String webhookUrl;

    @Column(name = "owner_email")
    private String ownerEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_crawled_at")
    private LocalDateTime lastCrawledAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}