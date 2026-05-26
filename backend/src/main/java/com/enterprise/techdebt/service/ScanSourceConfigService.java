package com.enterprise.techdebt.service;

import com.enterprise.techdebt.api.dto.UpsertScanSourceConfigRequest;
import com.enterprise.techdebt.audit.AuditAction;
import com.enterprise.techdebt.audit.AuditService;
import com.enterprise.techdebt.audit.ResourceType;
import com.enterprise.techdebt.domain.ProjectRole;
import com.enterprise.techdebt.domain.ScanSourceConfig;
import com.enterprise.techdebt.repo.ScanSourceConfigRepository;
import com.enterprise.techdebt.secret.SecretStore;
import com.enterprise.techdebt.security.AuthorizationService;
import com.enterprise.techdebt.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ScanSourceConfigService {

    private final ScanSourceConfigRepository repository;
    private final SecretStore secretStore;
    private final AuditService auditService;
    private final AuthorizationService authorizationService;

    public ScanSourceConfigService(ScanSourceConfigRepository repository,
                                   SecretStore secretStore,
                                   AuditService auditService,
                                   AuthorizationService authorizationService) {
        this.repository = repository;
        this.secretStore = secretStore;
        this.auditService = auditService;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<ScanSourceConfig> list(CurrentUser actor, long projectId) {
        authorizationService.requireProjectRole(actor, projectId, ProjectRole.VIEWER);
        return repository.findAllByProjectId(projectId);
    }

    @Transactional
    public ScanSourceConfig upsert(CurrentUser actor, long projectId, UpsertScanSourceConfigRequest req) {
        authorizationService.requireProjectRole(actor, projectId, ProjectRole.ADMIN);
        Instant now = Instant.now();

        ScanSourceConfig existing = repository.findByProjectIdAndSource(projectId, req.source()).orElse(null);
        String secretRef = secretStore.store(
                "scan-source/" + projectId + "/" + req.source().name().toLowerCase(),
                req.secretValue());

        ScanSourceConfig cfg;
        if (existing == null) {
            cfg = ScanSourceConfig.builder()
                    .projectId(projectId)
                    .source(req.source())
                    .externalProjectId(req.externalProjectId())
                    .apiBaseUrl(req.apiBaseUrl())
                    .secretKeyVaultRef(secretRef)
                    .enabled(req.enabled() == null || req.enabled())
                    .cronExpression(req.cronExpression())
                    .coverageThresholdPct(req.coverageThresholdPct())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        } else {
            // Rotate the secret cleanly: store new, delete old.
            String oldRef = existing.getSecretKeyVaultRef();
            existing.setExternalProjectId(req.externalProjectId());
            existing.setApiBaseUrl(req.apiBaseUrl());
            existing.setSecretKeyVaultRef(secretRef);
            if (req.enabled() != null) existing.setEnabled(req.enabled());
            existing.setCronExpression(req.cronExpression());
            existing.setCoverageThresholdPct(req.coverageThresholdPct());
            existing.setUpdatedAt(now);
            cfg = existing;
            try {
                secretStore.delete(oldRef);
            } catch (RuntimeException ignored) {
                // best-effort; orphaned secret is acceptable vs failing the write
            }
        }

        cfg = repository.save(cfg);
        auditService.record(actor, AuditAction.SCAN_SOURCE_CONFIGURED, ResourceType.SCAN_SOURCE_CONFIG,
                String.valueOf(cfg.getId()), projectId,
                null,
                Map.of(
                        "source", cfg.getSource().name(),
                        "externalProjectId", cfg.getExternalProjectId(),
                        "apiBaseUrl", cfg.getApiBaseUrl(),
                        "enabled", cfg.isEnabled()),
                null);
        return cfg;
    }

    @Transactional
    public void delete(CurrentUser actor, long projectId, long configId) {
        authorizationService.requireProjectRole(actor, projectId, ProjectRole.ADMIN);
        ScanSourceConfig cfg = repository.findById(configId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Scan source config not found"));
        if (!cfg.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(NOT_FOUND, "Scan source config not found in this project");
        }
        repository.delete(cfg);
        try {
            secretStore.delete(cfg.getSecretKeyVaultRef());
        } catch (RuntimeException ignored) {
        }
        auditService.record(actor, AuditAction.SCAN_SOURCE_CONFIGURED, ResourceType.SCAN_SOURCE_CONFIG,
                String.valueOf(cfg.getId()), projectId,
                Map.of("source", cfg.getSource().name()), Map.of("deleted", true), null);
    }
}
