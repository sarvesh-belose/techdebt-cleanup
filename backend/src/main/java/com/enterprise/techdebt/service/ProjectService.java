package com.enterprise.techdebt.service;

import com.enterprise.techdebt.api.dto.CreateProjectRequest;
import com.enterprise.techdebt.api.dto.UpdateProjectRequest;
import com.enterprise.techdebt.audit.AuditAction;
import com.enterprise.techdebt.audit.AuditService;
import com.enterprise.techdebt.audit.ResourceType;
import com.enterprise.techdebt.config.MdcConstants;
import com.enterprise.techdebt.domain.Project;
import com.enterprise.techdebt.domain.ProjectPermission;
import com.enterprise.techdebt.domain.ProjectRole;
import com.enterprise.techdebt.repo.ProjectPermissionRepository;
import com.enterprise.techdebt.repo.ProjectRepository;
import com.enterprise.techdebt.security.AuthorizationService;
import com.enterprise.techdebt.security.CurrentUser;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectPermissionRepository permissionRepository;
    private final AuditService auditService;
    private final AuthorizationService authorizationService;

    public ProjectService(ProjectRepository projectRepository,
                          ProjectPermissionRepository permissionRepository,
                          AuditService auditService,
                          AuthorizationService authorizationService) {
        this.projectRepository = projectRepository;
        this.permissionRepository = permissionRepository;
        this.auditService = auditService;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public Project create(CurrentUser actor, CreateProjectRequest req) {
        if (projectRepository.existsByNameIgnoreCase(req.name())) {
            throw new ResponseStatusException(CONFLICT, "Project name already exists: " + req.name());
        }
        Instant now = Instant.now();
        Project project = Project.builder()
                .name(req.name().trim())
                .description(req.description())
                .azureRepoUrl(req.azureRepoUrl().trim())
                .defaultBranch(req.effectiveBranch())
                .ownerUserId(actor.userId())
                .autoModeEnabled(false)
                .archived(false)
                .safetyThreshold(60)
                .autoRateLimitPerHour(5)
                .autoPolicyJson(null)
                .retentionDays(90)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            project = projectRepository.save(project);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(CONFLICT, "Project name already exists: " + req.name(), ex);
        }

        permissionRepository.save(ProjectPermission.builder()
                .projectId(project.getId())
                .userId(actor.userId())
                .role(ProjectRole.ADMIN)
                .grantedAt(now)
                .grantedByUserId(actor.userId())
                .build());

        MDC.put(MdcConstants.PROJECT_ID, String.valueOf(project.getId()));
        auditService.record(actor, AuditAction.PROJECT_CREATED, ResourceType.PROJECT,
                String.valueOf(project.getId()), project.getId(), null, project, null);
        return project;
    }

    @Transactional(readOnly = true)
    public Project getById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Project not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Project> list(boolean includeArchived, Pageable pageable) {
        if (includeArchived) {
            return projectRepository.findAll(pageable);
        }
        return projectRepository.findAllByArchived(false, pageable);
    }

    @Transactional
    public Project update(CurrentUser actor, Long id, UpdateProjectRequest req) {
        Project project = getById(id);
        authorizationService.requireProjectRole(actor, id, ProjectRole.ADMIN);

        Map<String, Object> before = snapshot(project);
        boolean autoModeBefore = project.isAutoModeEnabled();

        if (req.description() != null) {
            project.setDescription(req.description());
        }
        if (req.azureRepoUrl() != null && !req.azureRepoUrl().isBlank()) {
            project.setAzureRepoUrl(req.azureRepoUrl().trim());
        }
        if (req.defaultBranch() != null && !req.defaultBranch().isBlank()) {
            project.setDefaultBranch(req.defaultBranch().trim());
        }
        if (req.autoModeEnabled() != null) {
            project.setAutoModeEnabled(req.autoModeEnabled());
        }
        if (req.safetyThreshold() != null) {
            project.setSafetyThreshold(req.safetyThreshold());
        }
        if (req.autoRateLimitPerHour() != null) {
            project.setAutoRateLimitPerHour(req.autoRateLimitPerHour());
        }
        if (req.autoPolicyJson() != null) {
            // empty string clears the policy back to deny-all
            project.setAutoPolicyJson(req.autoPolicyJson().isBlank() ? null : req.autoPolicyJson());
        }
        project.setUpdatedAt(Instant.now());
        project = projectRepository.save(project);

        MDC.put(MdcConstants.PROJECT_ID, String.valueOf(project.getId()));
        auditService.record(actor, AuditAction.PROJECT_UPDATED, ResourceType.PROJECT,
                String.valueOf(project.getId()), project.getId(), before, snapshot(project), null);

        if (req.autoModeEnabled() != null && autoModeBefore != project.isAutoModeEnabled()) {
            auditService.record(actor, AuditAction.AUTO_MODE_TOGGLED, ResourceType.PROJECT,
                    String.valueOf(project.getId()), project.getId(),
                    Map.of("autoModeEnabled", autoModeBefore),
                    Map.of("autoModeEnabled", project.isAutoModeEnabled()),
                    null);
        }
        return project;
    }

    @Transactional
    public Project setArchived(CurrentUser actor, Long id, boolean archived) {
        Project project = getById(id);
        authorizationService.requireProjectRole(actor, id, ProjectRole.ADMIN);

        if (project.isArchived() == archived) {
            return project;
        }
        Map<String, Object> before = snapshot(project);
        project.setArchived(archived);
        project.setUpdatedAt(Instant.now());
        project = projectRepository.save(project);

        MDC.put(MdcConstants.PROJECT_ID, String.valueOf(project.getId()));
        auditService.record(actor,
                archived ? AuditAction.PROJECT_ARCHIVED : AuditAction.PROJECT_UNARCHIVED,
                ResourceType.PROJECT,
                String.valueOf(project.getId()), project.getId(),
                before, snapshot(project), null);
        return project;
    }

    private Map<String, Object> snapshot(Project p) {
        Map<String, Object> snap = new java.util.LinkedHashMap<>();
        snap.put("id", p.getId());
        snap.put("name", p.getName());
        snap.put("description", p.getDescription());
        snap.put("azureRepoUrl", p.getAzureRepoUrl());
        snap.put("defaultBranch", p.getDefaultBranch());
        snap.put("ownerUserId", p.getOwnerUserId());
        snap.put("autoModeEnabled", p.isAutoModeEnabled());
        snap.put("archived", p.isArchived());
        snap.put("safetyThreshold", p.getSafetyThreshold());
        snap.put("autoRateLimitPerHour", p.getAutoRateLimitPerHour());
        snap.put("autoPolicyJson", p.getAutoPolicyJson());
        return snap;
    }
}
