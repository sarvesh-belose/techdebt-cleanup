package com.enterprise.techdebt.secret;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Development-only secret store. Holds secrets in-process; never use outside local dev.
 */
@Component
@ConditionalOnProperty(name = "techdebt.secretstore.impl", havingValue = "in-memory", matchIfMissing = true)
public class InMemorySecretStore implements SecretStore {

    private final ConcurrentMap<String, String> secrets = new ConcurrentHashMap<>();

    @Override
    public String resolve(String ref) {
        String value = secrets.get(ref);
        if (value == null) {
            throw new SecretNotFoundException(ref);
        }
        return value;
    }

    @Override
    public String store(String logicalName, String value) {
        String ref = logicalName + ":" + UUID.randomUUID();
        secrets.put(ref, value);
        return ref;
    }

    @Override
    public boolean delete(String ref) {
        return secrets.remove(ref) != null;
    }
}
