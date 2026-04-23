package com.example.crawler_service.repository;

import com.example.crawler_service.model.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiteRepository extends JpaRepository<Site, UUID> {
    Optional<Site> findByRootUrl(String rootUrl);
}