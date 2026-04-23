// repository/LinkCheckResultRepository.java
package com.example.checker_service.repository;

import com.example.checker_service.model.LinkCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LinkCheckResultRepository extends JpaRepository<LinkCheckResult, UUID> {}