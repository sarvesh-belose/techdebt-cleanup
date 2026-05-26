package com.enterprise.techdebt.api;

import com.enterprise.techdebt.api.dto.ScanSourceConfigResponse;
import com.enterprise.techdebt.api.dto.UpsertScanSourceConfigRequest;
import com.enterprise.techdebt.security.CurrentUserService;
import com.enterprise.techdebt.service.ScanSourceConfigService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/scan-sources")
public class ScanSourceConfigController {

    private final ScanSourceConfigService service;
    private final CurrentUserService currentUserService;

    public ScanSourceConfigController(ScanSourceConfigService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public List<ScanSourceConfigResponse> list(@PathVariable Long projectId) {
        return service.list(currentUserService.require(), projectId).stream()
                .map(ScanSourceConfigResponse::from)
                .toList();
    }

    @PutMapping
    public ScanSourceConfigResponse upsert(@PathVariable Long projectId,
                                           @Valid @RequestBody UpsertScanSourceConfigRequest req) {
        return ScanSourceConfigResponse.from(
                service.upsert(currentUserService.require(), projectId, req));
    }

    @DeleteMapping("/{configId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId, @PathVariable Long configId) {
        service.delete(currentUserService.require(), projectId, configId);
        return ResponseEntity.noContent().build();
    }
}
