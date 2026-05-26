package com.enterprise.techdebt.api;

import com.enterprise.techdebt.compliance.ComplianceDossierService;
import com.enterprise.techdebt.security.CurrentUserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/fix-runs/{runId}")
public class ComplianceController {

    private final ComplianceDossierService dossierService;
    private final CurrentUserService currentUserService;

    public ComplianceController(ComplianceDossierService dossierService,
                                 CurrentUserService currentUserService) {
        this.dossierService = dossierService;
        this.currentUserService = currentUserService;
    }

    @GetMapping(value = "/dossier", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> dossier(@PathVariable Long projectId, @PathVariable Long runId) {
        currentUserService.require();
        String html = dossierService.renderHtml(projectId, runId);
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=\"fix-run-" + runId + "-dossier.html\"")
                .body(html);
    }
}
