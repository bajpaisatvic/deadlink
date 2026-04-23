package com.example.alert_service.repository;

import com.example.alert_service.model.AlertLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AlertLogRepository extends JpaRepository<AlertLog, UUID> {

    boolean existsByLinkIdAndAlertTypeAndFiredAtAfter(
            UUID linkId, String alertType, LocalDateTime after
    );
    List<AlertLog> findBySiteIdOrderByFiredAtDesc(UUID siteId);
}