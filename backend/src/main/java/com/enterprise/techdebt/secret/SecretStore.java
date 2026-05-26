package com.enterprise.techdebt.secret;

/**
 * Resolve secrets by reference. Implementations: in-memory (local dev),
 * Azure Key Vault (dev/prod). Database never stores raw secret bytes —
 * only the reference (e.g., the Key Vault secret name) is persisted.
 */
public interface SecretStore {

    /**
     * @param ref opaque secret reference (Key Vault secret name, or in-memory key)
     * @return the secret value
     * @throws SecretNotFoundException if no such secret exists
     */
    String resolve(String ref);

    /**
     * Store a secret and return the reference under which it can later be resolved.
     */
    String store(String logicalName, String value);

    /**
     * Remove a secret. Returns true if a secret existed and was removed.
     */
    boolean delete(String ref);
}
