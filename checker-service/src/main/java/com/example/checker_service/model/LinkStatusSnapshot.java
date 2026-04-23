package com.example.checker_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "link_status_snapshots", schema = "checker")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkStatusSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "link_id", nullable = false, unique = true)
    private UUID linkId;

    @Column(name = "current_status", nullable = false)
    private String currentStatus;

    @Column(name = "last_changed_at", nullable = false)
    private LocalDateTime lastChangedAt;

    @Column(name = "consecutive_failures", nullable = false)
    private Integer consecutiveFailures = 0;
}