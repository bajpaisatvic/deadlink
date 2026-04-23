package com.example.alert_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "alert_logs", schema = "alert")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Column(name = "fired_at", nullable = false)
    private LocalDateTime firedAt;

    @Column(nullable = false)
    private Boolean delivered;

    @PrePersist
    protected void onCreate() {
        this.firedAt = LocalDateTime.now();
    }
}