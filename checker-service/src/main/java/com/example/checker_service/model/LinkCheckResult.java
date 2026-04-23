package com.example.checker_service.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "link_check_results", schema = "checker")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkCheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "response_time")
    private Integer responseTime;

    @Column(nullable = false)
    private String status;

    @Column(name = "redirect_url")
    private String redirectUrl;

    @PrePersist
    protected void onCreate() {
        this.checkedAt = LocalDateTime.now();
    }
}