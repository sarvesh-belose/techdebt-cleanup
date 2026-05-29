package com.enterprise.techdebt.dev;

import com.enterprise.techdebt.api.dto.CreateProjectRequest;
import com.enterprise.techdebt.api.dto.UpsertScanSourceConfigRequest;
import com.enterprise.techdebt.domain.AppUser;
import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.Project;
import com.enterprise.techdebt.ingestion.IssueIngestionService;
import com.enterprise.techdebt.repo.AppUserRepository;
import com.enterprise.techdebt.repo.ProjectRepository;
import com.enterprise.techdebt.security.CurrentUser;
import com.enterprise.techdebt.service.ProjectService;
import com.enterprise.techdebt.service.ScanSourceConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Instant;

/**
 * Reads {@link DevBootstrapProperties} on startup and idempotently:
 *   1. Upserts each project by name (creates if missing, leaves existing alone)
 *   2. Upserts each scan source (delegates to ScanSourceConfigService which handles
 *      the create-or-update case + SecretStore rotation)
 *   3. If {@code triggerInitialScan: true}, fires a scan on the techdebt async
 *      executor so startup isn't blocked
 *
 * Active only on the {@code local} profile and only when
 * {@code techdebt.dev.bootstrap.enabled=true} (default false). The platform boots
 * cleanly with an empty state when this is off — manual UI registration still works.
 */
@Configuration
@EnableConfigurationProperties(DevBootstrapProperties.class)
@Profile("local")
@ConditionalOnProperty(name = "techdebt.dev.bootstrap.enabled", havingValue = "true")
public class DevBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevBootstrapRunner.class);
    private static final String BOOTSTRAP_UPN = "dev-bootstrap@local";

    private final DevBootstrapProperties props;
    private final ProjectService projectService;
    private final ScanSourceConfigService scanSourceService;
    private final IssueIngestionService ingestionService;
    private final ProjectRepository projectRepository;
    private final AppUserRepository userRepository;
    private final TaskExecutor asyncExecutor;

    public DevBootstrapRunner(DevBootstrapProperties props,
                              ProjectService projectService,
                              ScanSourceConfigService scanSourceService,
                              IssueIngestionService ingestionService,
                              ProjectRepository projectRepository,
                              AppUserRepository userRepository,
                              @Qualifier("techdebtAsyncExecutor") TaskExecutor asyncExecutor) {
        this.props = props;
        this.projectService = projectService;
        this.scanSourceService = scanSourceService;
        this.ingestionService = ingestionService;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.getProjects() == null || props.getProjects().isEmpty()) {
            log.info("dev.bootstrap enabled but no projects configured — nothing to do");
            return;
        }

        CurrentUser actor = systemActor();
        log.info("dev.bootstrap applying {} project(s)…", props.getProjects().size());

        for (DevBootstrapProperties.ProjectConfig cfg : props.getProjects()) {
            try {
                Project project = upsertProject(actor, cfg);
                for (DevBootstrapProperties.ScanSourceConfig source : cfg.getScanSources()) {
                    upsertScanSource(actor, project, source);
                }
                if (cfg.isTriggerInitialScan()) {
                    triggerInitialScans(actor, project, cfg);
                }
            } catch (RuntimeException ex) {
                log.warn("dev.bootstrap failed for project {}: {}", cfg.getName(), ex.getMessage());
            }
        }
        log.info("dev.bootstrap done");
    }

    private Project upsertProject(CurrentUser actor, DevBootstrapProperties.ProjectConfig cfg) {
        return projectRepository.findByNameIgnoreCase(cfg.getName())
                .orElseGet(() -> {
                    log.info("dev.bootstrap creating project {}", cfg.getName());
                    return projectService.create(actor, new CreateProjectRequest(
                            cfg.getName(),
                            cfg.getDescription(),
                            cfg.getRepoUrl(),
                            cfg.getDefaultBranch()));
                });
    }

    private void upsertScanSource(CurrentUser actor, Project project,
                                  DevBootstrapProperties.ScanSourceConfig src) {
        if (src.getSource() == null) {
            log.warn("dev.bootstrap project {} has a scan source missing 'source' — skipping",
                    project.getName());
            return;
        }
        if (isBlank(src.getSecret())) {
            log.warn("dev.bootstrap project {} {} secret is empty (placeholder env var unset?) — skipping",
                    project.getName(), src.getSource());
            return;
        }
        if (src.getSource() == IssueSource.JUNIT_COVERAGE) {
            log.warn("dev.bootstrap project {} declares JUNIT_COVERAGE — coverage flows via SonarQube, "
                    + "use SONARQUBE source with coverageThresholdPct instead",
                    project.getName());
            return;
        }
        UpsertScanSourceConfigRequest req = new UpsertScanSourceConfigRequest(
                src.getSource(),
                src.getExternalProjectId(),
                src.getApiBaseUrl(),
                src.getSecret(),
                src.getCronExpression(),
                src.getCoverageThresholdPct(),
                true);
        scanSourceService.upsert(actor, project.getId(), req);
        log.info("dev.bootstrap project {} scan source {} configured", project.getName(), src.getSource());
    }

    private void triggerInitialScans(CurrentUser actor, Project project,
                                     DevBootstrapProperties.ProjectConfig cfg) {
        for (DevBootstrapProperties.ScanSourceConfig src : cfg.getScanSources()) {
            if (src.getSource() == null || isBlank(src.getSecret())) continue;
            asyncExecutor.execute(() -> {
                try {
                    log.info("dev.bootstrap kicking off initial scan project={} source={}",
                            project.getName(), src.getSource());
                    ingestionService.scan(actor, project.getId(), src.getSource());
                } catch (RuntimeException ex) {
                    log.warn("dev.bootstrap initial scan failed for project={} source={}: {}",
                            project.getName(), src.getSource(), ex.getMessage());
                }
            });
        }
    }

    private CurrentUser systemActor() {
        AppUser u = userRepository.findByUpnIgnoreCase(BOOTSTRAP_UPN).orElseGet(() ->
                userRepository.save(AppUser.builder()
                        .upn(BOOTSTRAP_UPN)
                        .email(BOOTSTRAP_UPN)
                        .displayName("Dev bootstrap")
                        .enabled(true)
                        .createdAt(Instant.now())
                        .build()));
        return new CurrentUser(u.getId(), u.getUpn(), u.getEmail(), u.getDisplayName());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank() || s.startsWith("${") || s.equals("<veracode-workspace-guid>")
                || s.equals("<veracode-application-guid>");
    }
}
