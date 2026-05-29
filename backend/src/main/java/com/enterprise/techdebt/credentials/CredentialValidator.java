package com.enterprise.techdebt.credentials;

import com.enterprise.techdebt.domain.CredentialKind;

/**
 * Live-validate a credential against the upstream system. Implementations must:
 *   - Validate format before any HTTP call (no point spending a round-trip on a typo)
 *   - Use a short timeout (10s default) so the UI feels responsive
 *   - Map HTTP 401/403 to BAD_CREDENTIALS and network errors to UNREACHABLE
 *   - NEVER persist anything — the caller owns the SecretStore lifecycle
 */
public interface CredentialValidator {
    CredentialKind kind();

    ValidationResult validate(String baseUrl, String secret);
}
