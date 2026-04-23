// repository/LinkStatusSnapshotRepository.java
package com.example.checker_service.repository;

import com.example.checker_service.model.LinkStatusSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LinkStatusSnapshotRepository extends JpaRepository<LinkStatusSnapshot, UUID> {
    Optional<LinkStatusSnapshot> findByLinkId(UUID linkId);
}