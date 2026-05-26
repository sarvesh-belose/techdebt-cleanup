package com.enterprise.techdebt.scheduler;

import com.enterprise.techdebt.automode.SystemSettingsService;
import com.enterprise.techdebt.config.MdcConstants;
import com.enterprise.techdebt.domain.ScanSourceConfig;
import com.enterprise.techdebt.ingestion.IssueIngestionService;
import com.enterprise.techdebt.repo.AppUserRepository;
import com.enterprise.techdebt.repo.ScanSourceConfigRepository;
import com.enterprise.techdebt.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Runs every minute. Walks all enabled scan sources with a cron expression and triggers
 * the ones whose next-fire-time has passed since {@code last_scan_at}. Honors the global
 * auto-mode kill switch (no scheduled scans run while it's flipped, since scheduled scans
 * feed scheduled fixes).
 *
 * Bootstrap timestamp: when {@code last_scan_at} is null (never scanned), we treat the
 * created_at as the anchor so a brand-new source kicks off on its first match.
 */
@Component
public class ScheduledScanRunner {

    private static final Logger log = LoggerFactory.getLogger(ScheduledScanRunner.class);

    private final ScanSourceConfigRepository configRepository;
    private final IssueIngestionService ingestionService;
    private final AppUserRepository userRepository;
    private final SystemSettingsService systemSettings;
    private final boolean schedulingEnabled;
    private final ZoneId scheduleZone;

    public ScheduledScanRunner(ScanSourceConfigRepository configRepository,
                                IssueIngestionService ingestionService,
                                AppUserRepository userRepository,
                                SystemSettingsService systemSettings,
                                @Value("${techdebt.scheduler.enabled:true}") boolean schedulingEnabled,
                                @Value("${techdebt.scheduler.zone:UTC}") String zoneId) {
        this.configRepository = configRepository;
        this.ingestionService = ingestionService;
        this.userRepository = userRepository;
        this.systemSettings = systemSettings;
        this.schedulingEnabled = schedulingEnabled;
        this.scheduleZone = ZoneId.of(zoneId);
    }

    @Scheduled(cron = "${techdebt.scheduler.cron:0 * * * * *}")
    @Transactional
    public void runDueScans() {
        if (!schedulingEnabled) return;
        if (systemSettings.isAutoModeKilled()) {
            log.debug("Kill switch is on — skipping scheduled scans");
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(scheduleZone);
        String correlationId = "sched-" + UUID.randomUUID();
        MDC.put(MdcConstants.CORRELATION_ID, correlationId);
        try {
            for (ScanSourceConfig cfg : configRepository.findAll()) {
                if (!cfg.isEnabled() || cfg.getCronExpression() == null || cfg.getCronExpression().isBlank()) continue;
                CronExpression cron;
                try {
                    cron = CronExpression.parse(cfg.getCronExpression());
                } catch (IllegalArgumentException ex) {
                    log.warn("Skipping scan source {}/{} — invalid cron '{}': {}",
                            cfg.getProjectId(), cfg.getSource(), cfg.getCronExpression(), ex.getMessage());
                    continue;
                }
                ZonedDateTime anchor = cfg.getLastScanAt() != null
                        ? cfg.getLastScanAt().atZone(scheduleZone)
                        : cfg.getCreatedAt().atZone(scheduleZone);
                LocalDateTime nextFire = cron.next(anchor.toLocalDateTime());
                if (nextFire == null || nextFire.atZone(scheduleZone).isAfter(now)) continue;

                triggerScan(cfg);
            }
        } finally {
            MDC.remove(MdcConstants.CORRELATION_ID);
        }
    }

    private void triggerScan(ScanSourceConfig cfg) {
        MDC.put(MdcConstants.PROJECT_ID, String.valueOf(cfg.getProjectId()));
        log.info("Scheduled scan firing for project={} source={}", cfg.getProjectId(), cfg.getSource());
        CurrentUser scheduler = systemActor();
        try {
            ingestionService.scan(scheduler, cfg.getProjectId(), cfg.getSource());
            cfg.setLastScanAt(java.time.Instant.now());
            configRepository.save(cfg);
        } catch (RuntimeException ex) {
            log.warn("Scheduled scan failed for project={} source={}: {}",
                    cfg.getProjectId(), cfg.getSource(), ex.getMessage());
            // Don't update last_scan_at on failure so the next tick retries — but with a
            // 1-minute floor (the @Scheduled cron). The connector's own rate-limit handling
            // is responsible for not hammering upstream.
        }
    }

    private CurrentUser systemActor() {
        // Scheduled scans aren't tied to a human; we use a fixed "system" UPN that gets
        // upserted into app_user on first use so audit_event rows still have a valid actor.
        return userRepository.findByUpnIgnoreCase("system@scheduler")
                .map(u -> new CurrentUser(u.getId(), u.getUpn(), u.getEmail(), u.getDisplayName()))
                .orElseGet(() -> {
                    var u = userRepository.save(com.enterprise.techdebt.domain.AppUser.builder()
                            .upn("system@scheduler")
                            .email("system@scheduler")
                            .displayName("Scheduled scanner")
                            .enabled(true)
                            .createdAt(java.time.Instant.now())
                            .build());
                    return new CurrentUser(u.getId(), u.getUpn(), u.getEmail(), u.getDisplayName());
                });
    }
}
