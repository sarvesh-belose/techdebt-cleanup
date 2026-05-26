package com.enterprise.techdebt.repo;

import com.enterprise.techdebt.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Append-only repository — deliberately exposes only insert and read.
 * No save() update path, no delete*() — this is the application-side
 * guarantee for tamper-evident audit logs. DB-level INSTEAD OF triggers
 * are added separately on MSSQL deployments.
 */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {
    AuditEvent save(AuditEvent event);

    Page<AuditEvent> findAllByProjectId(Long projectId, Pageable pageable);

    Page<AuditEvent> findAll(Pageable pageable);
}
