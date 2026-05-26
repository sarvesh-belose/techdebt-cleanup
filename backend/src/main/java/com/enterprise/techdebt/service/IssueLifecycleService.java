package com.enterprise.techdebt.service;

import com.enterprise.techdebt.audit.AuditAction;
import com.enterprise.techdebt.audit.AuditService;
import com.enterprise.techdebt.audit.ResourceType;
import com.enterprise.techdebt.domain.Issue;
import com.enterprise.techdebt.domain.IssueStatus;
import com.enterprise.techdebt.domain.ProjectRole;
import com.enterprise.techdebt.repo.IssueRepository;
import com.enterprise.techdebt.security.AuthorizationService;
import com.enterprise.techdebt.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class IssueLifecycleService {

    private final IssueRepository issueRepository;
    private final AuditService auditService;
    private final AuthorizationService authorizationService;

    public IssueLifecycleService(IssueRepository issueRepository,
                                  AuditService auditService,
                                  AuthorizationService authorizationService) {
        this.issueRepository = issueRepository;
        this.auditService = auditService;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public Issue markWontFix(CurrentUser actor, long projectId, long issueId, String reason) {
        Issue issue = loadForProject(projectId, issueId);
        authorizationService.requireProjectRole(actor, projectId, ProjectRole.APPROVER);
        IssueStatus before = issue.getStatus();
        issue.setStatus(IssueStatus.WONT_FIX);
        issue.setWontFixReason(reason);
        issue.setUpdatedAt(Instant.now());
        issue = issueRepository.save(issue);
        auditService.record(actor, AuditAction.ISSUE_STATUS_CHANGED, ResourceType.ISSUE,
                String.valueOf(issueId), projectId,
                Map.of("status", before.name()),
                Map.of("status", IssueStatus.WONT_FIX.name(), "reason", reason),
                reason);
        return issue;
    }

    @Transactional
    public Issue reopen(CurrentUser actor, long projectId, long issueId) {
        Issue issue = loadForProject(projectId, issueId);
        authorizationService.requireProjectRole(actor, projectId, ProjectRole.DEVELOPER);
        IssueStatus before = issue.getStatus();
        issue.setStatus(IssueStatus.OPEN);
        issue.setWontFixReason(null);
        issue.setUpdatedAt(Instant.now());
        issue = issueRepository.save(issue);
        auditService.record(actor, AuditAction.ISSUE_STATUS_CHANGED, ResourceType.ISSUE,
                String.valueOf(issueId), projectId,
                Map.of("status", before.name()),
                Map.of("status", IssueStatus.OPEN.name()),
                null);
        return issue;
    }

    private Issue loadForProject(long projectId, long issueId) {
        Issue i = issueRepository.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Issue not found: " + issueId));
        if (!i.getProjectId().equals(projectId)) {
            throw new ResponseStatusException(NOT_FOUND,
                    "Issue " + issueId + " does not belong to project " + projectId);
        }
        return i;
    }
}
