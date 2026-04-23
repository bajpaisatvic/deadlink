package com.example.crawler_service.repository;

import com.example.crawler_service.model.DiscoveredLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DiscoveredLinkRepository extends JpaRepository<DiscoveredLink, UUID> {

    boolean existsBySiteIdAndUrl(UUID siteId, String url);
}