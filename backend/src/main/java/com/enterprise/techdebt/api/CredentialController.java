package com.enterprise.techdebt.api;

import com.enterprise.techdebt.api.dto.CredentialResponse;
import com.enterprise.techdebt.api.dto.TestCredentialRequest;
import com.enterprise.techdebt.api.dto.TestCredentialResponse;
import com.enterprise.techdebt.api.dto.UpsertCredentialRequest;
import com.enterprise.techdebt.credentials.PlatformCredentialService;
import com.enterprise.techdebt.domain.CredentialKind;
import com.enterprise.techdebt.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/credentials")
public class CredentialController {

    private final PlatformCredentialService service;
    private final CurrentUserService currentUserService;

    public CredentialController(PlatformCredentialService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    /** List configured credentials (metadata + last-validated state, never the secret). */
    @GetMapping
    public List<CredentialResponse> list() {
        currentUserService.require();
        return service.list().stream().map(CredentialResponse::from).toList();
    }

    /** Run live validation against the supplied URL+secret WITHOUT persisting. */
    @PostMapping("/{kind}/test")
    public TestCredentialResponse test(@PathVariable CredentialKind kind,
                                        @Valid @RequestBody TestCredentialRequest req) {
        currentUserService.require();
        return TestCredentialResponse.from(service.test(kind, req.baseUrl(), req.secret()));
    }

    /**
     * Validate then save. By default the save is rejected if validation fails
     * (returns 400 with the validation detail). Pass {@code saveAnyway=true} to
     * override (audit-logged).
     */
    @PutMapping("/{kind}")
    public ResponseEntity<?> upsert(@PathVariable CredentialKind kind,
                                     @Valid @RequestBody UpsertCredentialRequest req) {
        var actor = currentUserService.require();
        boolean saveAnyway = Boolean.TRUE.equals(req.saveAnyway());
        var outcome = service.upsert(actor, kind, req.baseUrl(), req.secret(), saveAnyway);
        if (!outcome.persisted()) {
            // Return the validation detail with 400 so the UI can show the reason
            // without the user losing what they typed.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "ok", false,
                    "status", outcome.validation().status(),
                    "message", outcome.validation().message(),
                    "hint", "Send saveAnyway=true to persist despite validation failure"));
        }
        return ResponseEntity.ok(Map.of(
                "credential", CredentialResponse.from(outcome.credential()),
                "validation", TestCredentialResponse.from(outcome.validation())));
    }

    @DeleteMapping("/{kind}")
    public ResponseEntity<Void> delete(@PathVariable CredentialKind kind) {
        var actor = currentUserService.require();
        boolean removed = service.delete(actor, kind);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
