package com.enterprise.techdebt.repo;

import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.ScanSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScanSourceConfigRepository extends JpaRepository<ScanSourceConfig, Long> {
    List<ScanSourceConfig> findAllByProjectId(Long projectId);

    Optional<ScanSourceConfig> findByProjectIdAndSource(Long projectId, IssueSource source);
}
