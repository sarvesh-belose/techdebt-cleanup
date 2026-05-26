package com.enterprise.techdebt.api;

import com.enterprise.techdebt.api.dto.IssueResponse;
import com.enterprise.techdebt.api.dto.PageResponse;
import com.enterprise.techdebt.api.dto.WontFixRequest;
import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.IssueStatus;
import com.enterprise.techdebt.domain.Severity;
import com.enterprise.techdebt.security.CurrentUserService;
import com.enterprise.techdebt.service.IssueLifecycleService;
import com.enterprise.techdebt.service.IssueQueryService;
import com.enterprise.techdebt.service.IssueQueryService.IssueQuery;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/projects/{projectId}/issues")
public class IssueController {

    private final IssueQueryService queryService;
    private final IssueLifecycleService lifecycleService;
    private final CurrentUserService currentUserService;

    public IssueController(IssueQueryService queryService,
                           IssueLifecycleService lifecycleService,
                           CurrentUserService currentUserService) {
        this.queryService = queryService;
        this.lifecycleService = lifecycleService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public PageResponse<IssueResponse> list(
            @PathVariable Long projectId,
            @RequestParam(required = false) IssueSource source,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) String filePath,
            @RequestParam(required = false) String cve,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @PageableDefault(size = 50, sort = "severity") Pageable pageable) {
        currentUserService.require();
        IssueQuery q = new IssueQuery();
        q.source = source;
        q.severity = severity;
        q.status = status;
        q.filePathContains = filePath;
        q.cve = cve;
        q.dateFrom = dateFrom;
        q.dateTo = dateTo;
        return PageResponse.of(queryService.list(projectId, q, pageable), IssueResponse::from);
    }

    @PostMapping("/{issueId}/wont-fix")
    public IssueResponse markWontFix(@PathVariable Long projectId,
                                      @PathVariable Long issueId,
                                      @Valid @RequestBody WontFixRequest req) {
        return IssueResponse.from(
                lifecycleService.markWontFix(currentUserService.require(), projectId, issueId, req.reason()));
    }

    @PostMapping("/{issueId}/reopen")
    public IssueResponse reopen(@PathVariable Long projectId, @PathVariable Long issueId) {
        return IssueResponse.from(
                lifecycleService.reopen(currentUserService.require(), projectId, issueId));
    }
}
