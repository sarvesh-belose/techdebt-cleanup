package com.enterprise.techdebt.credentials;

import com.enterprise.techdebt.audit.AuditAction;
import com.enterprise.techdebt.audit.AuditService;
import com.enterprise.techdebt.audit.ResourceType;
import com.enterprise.techdebt.domain.CredentialKind;
import com.enterprise.techdebt.domain.PlatformCredential;
import com.enterprise.techdebt.repo.PlatformCredentialRepository;
import com.enterprise.techdebt.secret.SecretStore;
import com.enterprise.techdebt.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PlatformCredentialService {

    private static final Logger log = LoggerFactory.getLogger(PlatformCredentialService.class);

    private final PlatformCredentialRepository repository;
    private final SecretStore secretStore;
    private final AuditService auditService;
    private final Map<CredentialKind, CredentialValidator> validators = new EnumMap<>(CredentialKind.class);

    public PlatformCredentialService(PlatformCredentialRepository repository,
                                      SecretStore secretStore,
                                      AuditService auditService,
                                      List<CredentialValidator> validators) {
        this.repository = repository;
        this.secretStore = secretStore;
        this.auditService = auditService;
        for (CredentialValidator v : validators) {
            this.validators.put(v.kind(), v);
        }
    }

    public ValidationResult test(CredentialKind kind, String baseUrl, String secret) {
        CredentialValidator v = validators.get(kind);
        if (v == null) {
            return ValidationResult.upstreamError("No validator registered for " + kind);
        }
        return v.validate(baseUrl, secret);
    }

    @Transactional(readOnly = true)
    public List<PlatformCredential> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<PlatformCredential> get(CredentialKind kind) {
        return repository.findByKind(kind);
    }

    /**
     * Validate, then save. If validation fails AND saveAnyway is false, the credential
     * is not persisted and the existing one (if any) is left untouched. saveAnyway is
     * an escape hatch for credentials that work in production but can't be validated
     * from the developer's network (corporate egress / firewalled Veracode endpoint).
     */
    @Transactional
    public UpsertOutcome upsert(CurrentUser actor, CredentialKind kind,
                                 String baseUrl, String secret, boolean saveAnyway) {
        ValidationResult result = test(kind, baseUrl, secret);
        if (!result.ok() && !saveAnyway) {
            return new UpsertOutcome(null, result, false);
        }

        Instant now = Instant.now();
        PlatformCredential existing = repository.findByKind(kind).orElse(null);
        String previousRef = existing != null ? existing.getSecretKeyVaultRef() : null;
        String newRef = secretStore.store("platform-credential/" + kind.name().toLowerCase(), secret);

        PlatformCredential cred = existing != null ? existing : PlatformCredential.builder()
                .kind(kind)
                .updatedAt(now)
                .build();
        cred.setBaseUrl(baseUrl);
        cred.setSecretKeyVaultRef(newRef);
        cred.setLastValidatedAt(now);
        cred.setLastValidatedOk(result.ok());
        cred.setLastValidatedMessage(result.message());
        cred.setUpdatedAt(now);
        cred.setUpdatedByUserId(actor != null ? actor.userId() : null);
        cred = repository.save(cred);

        if (previousRef != null) {
            try {
                secretStore.delete(previousRef);
            } catch (RuntimeException e) {
                log.debug("could not delete previous secret ref {}: {}", previousRef, e.getMessage());
            }
        }
        auditService.record(actor, AuditAction.SCAN_SOURCE_CONFIGURED,
                ResourceType.SCAN_SOURCE_CONFIG, "platform:" + kind.name(), null,
                null,
                Map.of("kind", kind.name(),
                       "baseUrl", baseUrl,
                       "validationOk", result.ok(),
                       "validationStatus", result.status(),
                       "saveAnyway", saveAnyway),
                result.message());
        return new UpsertOutcome(cred, result, true);
    }

    @Transactional
    public boolean delete(CurrentUser actor, CredentialKind kind) {
        PlatformCredential existing = repository.findByKind(kind).orElse(null);
        if (existing == null) return false;
        String ref = existing.getSecretKeyVaultRef();
        repository.delete(existing);
        try {
            secretStore.delete(ref);
        } catch (RuntimeException ignored) {
        }
        auditService.record(actor, AuditAction.SCAN_SOURCE_CONFIGURED,
                ResourceType.SCAN_SOURCE_CONFIG, "platform:" + kind.name(), null,
                Map.of("kind", kind.name()),
                Map.of("deleted", true), null);
        return true;
    }

    public record UpsertOutcome(PlatformCredential credential, ValidationResult validation, boolean persisted) {
    }
}
