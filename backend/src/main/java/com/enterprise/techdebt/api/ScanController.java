package com.enterprise.techdebt.api;

import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.ingestion.IngestionResult;
import com.enterprise.techdebt.ingestion.IssueIngestionService;
import com.enterprise.techdebt.security.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/scans")
public class ScanController {

    private final IssueIngestionService ingestionService;
    private final CurrentUserService currentUserService;

    public ScanController(IssueIngestionService ingestionService, CurrentUserService currentUserService) {
        this.ingestionService = ingestionService;
        this.currentUserService = currentUserService;
    }

    /**
     * Synchronous scan for now — short-running connectors (Veracode SCA/SonarQube) are
     * fine for a foreground call in pilot. Sprint 6 (M3) introduces async + SSE progress
     * for long-running scans across large projects.
     */
    @PostMapping
    public ResponseEntity<IngestionResult> trigger(@PathVariable Long projectId,
                                                    @RequestParam(name = "source") IssueSource source) {
        IngestionResult result = ingestionService.scan(currentUserService.require(), projectId, source);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }
}
