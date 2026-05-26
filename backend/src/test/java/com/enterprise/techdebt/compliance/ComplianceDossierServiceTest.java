package com.enterprise.techdebt.compliance;

import com.enterprise.techdebt.api.dto.CreateProjectRequest;
import com.enterprise.techdebt.domain.FixRun;
import com.enterprise.techdebt.orchestrator.FixRunService;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class ComplianceDossierServiceTest {

    @Autowired ComplianceDossierService service;
    @Autowired ProjectService projectService;
    @Autowired CurrentUserService currentUserService;
    @Autowired FixRunService fixRunService;
    @Autowired FixRunRepository fixRunRepository;
    @Autowired ProjectRepository projectRepo;
    @Autowired ProjectPermissionRepository permissionRepo;
    @Autowired IssueRepository issueRepo;
    @Autowired ScanSourceConfigRepository scanCfgRepo;
    @Autowired FixAttemptRepository attemptRepo;
    @Autowired NotificationRepository notificationRepo;
    @Autowired AppUserRepository userRepo;

    @BeforeEach
    void cleanSlate() {
        notificationRepo.deleteAll();
        attemptRepo.deleteAll();
        fixRunRepository.deleteAll();
        issueRepo.deleteAll();
        scanCfgRepo.deleteAll();
        permissionRepo.deleteAll();
        projectRepo.deleteAll();
        userRepo.deleteAll();
    }

    @Test
    void rendersHtmlWithExpectedSections() {
        var actor = currentUserService.require();
        var project = projectService.create(actor, new CreateProjectRequest(
                "dossier-svc", null,
                "https://dev.azure.com/acme/banking/_git/dossier", "main"));
        FixRun run = fixRunService.create(actor, project.getId(), List.of(), false);

        String html = service.renderHtml(project.getId(), run.getId());

        assertThat(html).contains("Fix Run #" + run.getId());
        assertThat(html).contains("dossier-svc");
        assertThat(html).contains("<h2>Project</h2>");
        assertThat(html).contains("<h2>Run</h2>");
        assertThat(html).contains("<h2>Issues fixed (0)</h2>");
        assertThat(html).contains("<h2>Attempts (0)</h2>");
        assertThat(html).contains("End of dossier");
    }

    @Test
    void rejectsRunFromDifferentProject() {
        var actor = currentUserService.require();
        var p1 = projectService.create(actor, new CreateProjectRequest(
                "alpha", null, "https://dev.azure.com/x/y/_git/alpha", "main"));
        var p2 = projectService.create(actor, new CreateProjectRequest(
                "beta", null, "https://dev.azure.com/x/y/_git/beta", "main"));
        FixRun run = fixRunService.create(actor, p1.getId(), List.of(), false);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.renderHtml(p2.getId(), run.getId()));
    }
}
