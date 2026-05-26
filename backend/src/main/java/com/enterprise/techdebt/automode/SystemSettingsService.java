package com.enterprise.techdebt.automode;

import com.enterprise.techdebt.audit.AuditAction;
import com.enterprise.techdebt.audit.AuditService;
import com.enterprise.techdebt.audit.ResourceType;
import com.enterprise.techdebt.domain.SystemSetting;
import com.enterprise.techdebt.repo.SystemSettingRepository;
import com.enterprise.techdebt.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny key/value store for org-wide tunables. Reads are cached in-process so the
 * orchestrator + scheduler can consult the kill switch on every decision without
 * hammering the DB. The cache is invalidated on every write through this service —
 * external writes (DB hand-edits during incidents) take effect on next restart.
 */
@Service
public class SystemSettingsService {

    public static final String AUTO_MODE_KILL_SWITCH = "auto_mode.kill_switch";

    private final SystemSettingRepository repository;
    private final AuditService auditService;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean cacheLoaded;

    public SystemSettingsService(SystemSettingRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Optional<String> get(String key) {
        ensureLoaded();
        return Optional.ofNullable(cache.get(key));
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return get(key).map("true"::equalsIgnoreCase).orElse(defaultValue);
    }

    @Transactional
    public void set(CurrentUser actor, String key, String value) {
        Instant now = Instant.now();
        SystemSetting s = repository.findById(key).orElseGet(() -> SystemSetting.builder()
                .settingKey(key)
                .updatedAt(now)
                .build());
        String previous = s.getValueText();
        s.setValueText(value);
        s.setUpdatedAt(now);
        s.setUpdatedBy(actor != null ? actor.upn() : "system");
        repository.save(s);
        cache.put(key, value);
        auditService.record(actor, AuditAction.AUTO_MODE_TOGGLED, ResourceType.USER,
                key, null,
                Map.of("value", previous == null ? "" : previous),
                Map.of("value", value == null ? "" : value),
                "system setting changed: " + key);
    }

    public boolean isAutoModeKilled() {
        return getBoolean(AUTO_MODE_KILL_SWITCH, false);
    }

    @Transactional
    public void setAutoModeKilled(CurrentUser actor, boolean killed) {
        set(actor, AUTO_MODE_KILL_SWITCH, Boolean.toString(killed));
    }

    private void ensureLoaded() {
        if (cacheLoaded) return;
        synchronized (this) {
            if (cacheLoaded) return;
            for (SystemSetting s : repository.findAll()) {
                if (s.getValueText() != null) {
                    cache.put(s.getSettingKey(), s.getValueText());
                }
            }
            cacheLoaded = true;
        }
    }
}
