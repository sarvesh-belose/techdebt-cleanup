package com.enterprise.techdebt.ingestion;

import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.IssueStatus;
import com.enterprise.techdebt.domain.ScanSourceConfig;
import com.enterprise.techdebt.domain.Severity;
import com.enterprise.techdebt.repo.AppUserRepository;
import com.enterprise.techdebt.repo.FixAttemptRepository;
import com.enterprise.techdebt.repo.FixRunRepository;
import com.enterprise.techdebt.repo.IssueRepository;
import com.enterprise.techdebt.repo.NotificationRepository;
import com.enterprise.techdebt.repo.ProjectPermissionRepository;
import com.enterprise.techdebt.repo.ProjectRepository;
import com.enterprise.techdebt.repo.ScanSourceConfigRepository;
import com.enterprise.techdebt.security.CurrentUserService;
import com.enterprise.techdebt.service.ProjectService;
import com.enterprise.techdebt.api.dto.CreateProjectRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@org.springframework.context.annotation.Import(IssueIngestionServiceTest.FixtureConfig.class)
class IssueIngestionServiceTest {

    @TestConfiguration
    static class FixtureConfig {
        static final List<RawIssue> fixture = new ArrayList<>();

        @Bean
        @Primary
        @Order(Ordered.HIGHEST_PRECEDENCE)
        IssueSourceConnector fakeConnector() {
            return new IssueSourceConnector() {
                @Override public IssueSource source() { return IssueSource.VERACODE_SCA; }
                @Override public List<RawIssue> fetchIssues(ScanSourceConfig cfg) {
                    return new ArrayList<>(fixture);
                }
            };
        }
    }

    @Autowired IssueIngestionService ingestionService;
    @Autowired ProjectRepository projectRepo;
    @Autowired AppUserRepository userRepo;
    @Autowired ProjectPermissionRepository permissionRepo;
    @Autowired ScanSourceConfigRepository scanCfgRepo;
    @Autowired IssueRepository issueRepo;
    @Autowired NotificationRepository notificationRepo;
    @Autowired FixRunRepository fixRunRepo;
    @Autowired FixAttemptRepository attemptRepo;
    @Autowired CurrentUserService currentUserService;
    @Autowired ProjectService projectService;

    private long projectId;

    @BeforeEach
    void seed() {
        notificationRepo.deleteAll();
        attemptRepo.deleteAll();
        fixRunRepo.deleteAll();
        issueRepo.deleteAll();
        scanCfgRepo.deleteAll();
        permissionRepo.deleteAll();
        projectRepo.deleteAll();
        userRepo.deleteAll();
        FixtureConfig.fixture.clear();

        var actor = currentUserService.require();
        var p = projectService.create(actor,
                new CreateProjectRequest(
                        "ingest-svc",
                        null,
                        "https://dev.azure.com/acme/banking/_git/ingest",
                        "main"));
        projectId = p.getId();

        Instant now = Instant.now();
        scanCfgRepo.save(ScanSourceConfig.builder()
                .projectId(projectId)
                .source(IssueSource.VERACODE_SCA)
                .externalProjectId("workspace-1")
                .apiBaseUrl("https://api.veracode.com")
                .secretKeyVaultRef("dummy-ref")
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    @Test
    void firstScanCreatesIssues() {
        FixtureConfig.fixture.add(raw("EXT-1", Severity.CRITICAL, "CVE-2021-44228"));
        FixtureConfig.fixture.add(raw("EXT-2", Severity.HIGH, "CVE-2022-22965"));

        var result = ingestionService.scan(currentUserService.require(), projectId, IssueSource.VERACODE_SCA);

        assertThat(result.getCreated()).isEqualTo(2);
        assertThat(result.getUpdated()).isZero();
        assertThat(result.getResolvedExternally()).isZero();
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(issueRepo.findAll()).hasSize(2);
    }

    @Test
    void rescanWithSameSetUpdatesNotDuplicates() {
        FixtureConfig.fixture.add(raw("EXT-1", Severity.CRITICAL, "CVE-2021-44228"));
        ingestionService.scan(currentUserService.require(), projectId, IssueSource.VERACODE_SCA);

        // re-run same fixture
        var second = ingestionService.scan(currentUserService.require(), projectId, IssueSource.VERACODE_SCA);

        assertThat(second.getCreated()).isZero();
        assertThat(second.getUpdated()).isEqualTo(1);
        assertThat(issueRepo.findAll()).hasSize(1);
    }

    @Test
    void issuesNoLongerReturnedAreResolvedExternally() {
        FixtureConfig.fixture.add(raw("EXT-1", Severity.CRITICAL, "CVE-2021-44228"));
        FixtureConfig.fixture.add(raw("EXT-2", Severity.HIGH, "CVE-2022-22965"));
        ingestionService.scan(currentUserService.require(), projectId, IssueSource.VERACODE_SCA);

        FixtureConfig.fixture.clear();
        FixtureConfig.fixture.add(raw("EXT-1", Severity.CRITICAL, "CVE-2021-44228")); // only EXT-1 remains

        var second = ingestionService.scan(currentUserService.require(), projectId, IssueSource.VERACODE_SCA);
        assertThat(second.getResolvedExternally()).isEqualTo(1);

        var ext2 = issueRepo.findByProjectIdAndSourceAndExternalId(
                projectId, IssueSource.VERACODE_SCA, "EXT-2").orElseThrow();
        assertThat(ext2.getStatus()).isEqualTo(IssueStatus.FIXED_EXTERNALLY);
    }

    private RawIssue raw(String externalId, Severity sev, String cve) {
        return RawIssue.builder()
                .source(IssueSource.VERACODE_SCA)
                .externalId(externalId)
                .severity(sev)
                .title("CVE " + cve)
                .cve(cve)
                .componentName("org.apache.logging.log4j:log4j-core")
                .currentVersion("2.14.0")
                .safeVersion("2.17.1")
                .transitive(false)
                .build();
    }
}
