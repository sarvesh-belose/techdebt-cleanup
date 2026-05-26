package com.enterprise.techdebt.api;

import com.enterprise.techdebt.api.dto.FixRunResponse;
import com.enterprise.techdebt.api.dto.PageResponse;
import com.enterprise.techdebt.api.dto.StartFixRunRequest;
import com.enterprise.techdebt.domain.FixRun;
import com.enterprise.techdebt.orchestrator.FixOrchestrator;
import com.enterprise.techdebt.orchestrator.FixRunService;
import com.enterprise.techdebt.repo.FixRunRepository;
import com.enterprise.techdebt.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/fix-runs")
public class FixRunController {

    private final FixRunService fixRunService;
    private final FixOrchestrator orchestrator;
    private final FixRunRepository fixRunRepository;
    private final CurrentUserService currentUserService;

    public FixRunController(FixRunService fixRunService,
                            FixOrchestrator orchestrator,
                            FixRunRepository fixRunRepository,
                            CurrentUserService currentUserService) {
        this.fixRunService = fixRunService;
        this.orchestrator = orchestrator;
        this.fixRunRepository = fixRunRepository;
        this.currentUserService = currentUserService;
    }

    @PostMapping
    public ResponseEntity<FixRunResponse> start(@PathVariable Long projectId,
                                                 @Valid @RequestBody StartFixRunRequest req) {
        var actor = currentUserService.require();
        FixRun run = fixRunService.create(actor, projectId, req.issueIds(),
                Boolean.TRUE.equals(req.autoMode()));
        // Async — orchestrator runs on techdebtAsyncExecutor; clients poll GET /fix-runs/{id}.
        orchestrator.executeAsync(actor, run.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .header("Location", "/api/projects/" + projectId + "/fix-runs/" + run.getId())
                .body(FixRunResponse.from(run));
    }

    @GetMapping
    public PageResponse<FixRunResponse> list(@PathVariable Long projectId,
                                              @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        currentUserService.require();
        return PageResponse.of(
                fixRunRepository.findAllByProjectId(projectId, pageable),
                FixRunResponse::from);
    }

    @GetMapping("/{runId}")
    public FixRunResponse get(@PathVariable Long projectId, @PathVariable Long runId) {
        currentUserService.require();
        return FixRunResponse.from(fixRunService.get(runId));
    }

    @PostMapping("/{runId}/abort")
    public FixRunResponse abort(@PathVariable Long projectId, @PathVariable Long runId,
                                @RequestParam(defaultValue = "manual abort") String reason) {
        return FixRunResponse.from(
                fixRunService.abort(currentUserService.require(), runId, reason));
    }
}
