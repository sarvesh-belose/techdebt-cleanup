package com.enterprise.techdebt.automode;

import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.Severity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-project policy controlling which (source, severity) combinations are eligible
 * for auto-mode. Persisted as JSON on {@code project.auto_policy_json}.
 *
 * Default (null JSON or empty) is "deny all" — auto-mode toggle alone is not enough,
 * an explicit allowlist per source is required.
 */
public final class AutoModePolicy {

    private static final Logger log = LoggerFactory.getLogger(AutoModePolicy.class);

    private final Map<IssueSource, Set<Severity>> allow;

    public AutoModePolicy(Map<IssueSource, Set<Severity>> allow) {
        Map<IssueSource, Set<Severity>> normalized = new EnumMap<>(IssueSource.class);
        for (Map.Entry<IssueSource, Set<Severity>> e : allow.entrySet()) {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                normalized.put(e.getKey(), EnumSet.copyOf(e.getValue()));
            }
        }
        this.allow = normalized;
    }

    public boolean isEligible(IssueSource source, Severity severity) {
        Set<Severity> allowed = allow.get(source);
        return allowed != null && allowed.contains(severity);
    }

    public boolean isEmpty() {
        return allow.isEmpty();
    }

    public Map<IssueSource, Set<Severity>> asMap() {
        Map<IssueSource, Set<Severity>> out = new EnumMap<>(IssueSource.class);
        for (Map.Entry<IssueSource, Set<Severity>> e : allow.entrySet()) {
            out.put(e.getKey(), EnumSet.copyOf(e.getValue()));
        }
        return out;
    }

    public static AutoModePolicy empty() {
        return new AutoModePolicy(Map.of());
    }

    public static AutoModePolicy fromJson(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) return empty();
        try {
            Map<IssueSource, Set<Severity>> map = mapper.readValue(
                    json, new TypeReference<>() {});
            return new AutoModePolicy(map);
        } catch (JsonProcessingException e) {
            log.warn("Invalid auto_policy_json — treating as empty: {}", e.getMessage());
            return empty();
        }
    }

    public String toJson(ObjectMapper mapper) {
        try {
            return mapper.writeValueAsString(asMap());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize AutoModePolicy: {}", e.getMessage());
            return null;
        }
    }
}
