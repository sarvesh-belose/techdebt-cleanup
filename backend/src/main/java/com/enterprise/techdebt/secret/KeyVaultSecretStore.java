package com.enterprise.techdebt.secret;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Azure Key Vault implementation — stub. Wire azure-security-keyvault-secrets
 * SDK in Sprint 1 hardening: a SecretClient built from DefaultAzureCredential
 * resolves names to KeyVaultSecret values; store(...) becomes setSecret(...).
 *
 * Kept as a stub so the application can boot and the dependency-injection wiring
 * is verified end-to-end before the SDK + tenant config land.
 */
@Component
@ConditionalOnProperty(name = "techdebt.secretstore.impl", havingValue = "keyvault")
public class KeyVaultSecretStore implements SecretStore {

    private static final Logger log = LoggerFactory.getLogger(KeyVaultSecretStore.class);

    public KeyVaultSecretStore() {
        log.warn("KeyVaultSecretStore is a stub. Implement against azure-security-keyvault-secrets before non-local use.");
    }

    @Override
    public String resolve(String ref) {
        throw new UnsupportedOperationException("KeyVaultSecretStore.resolve not implemented yet");
    }

    @Override
    public String store(String logicalName, String value) {
        throw new UnsupportedOperationException("KeyVaultSecretStore.store not implemented yet");
    }

    @Override
    public boolean delete(String ref) {
        throw new UnsupportedOperationException("KeyVaultSecretStore.delete not implemented yet");
    }
}
