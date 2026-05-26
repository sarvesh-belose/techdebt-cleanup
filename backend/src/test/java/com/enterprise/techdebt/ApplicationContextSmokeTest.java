package com.enterprise.techdebt;

import com.enterprise.techdebt.repo.AppUserRepository;
import com.enterprise.techdebt.repo.AuditEventRepository;
import com.enterprise.techdebt.repo.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: Spring boots, Flyway runs against H2 (MSSQL mode), Hibernate validates
 * the schema against entities, and core repositories are wired.
 */
@SpringBootTest
@ActiveProfiles("local")
class ApplicationContextSmokeTest {

    @Autowired ProjectRepository projectRepository;
    @Autowired AppUserRepository appUserRepository;
    @Autowired AuditEventRepository auditEventRepository;

    @Test
    void contextLoadsAndRepositoriesAreWired() {
        assertThat(projectRepository).isNotNull();
        assertThat(appUserRepository).isNotNull();
        assertThat(auditEventRepository).isNotNull();
        assertThat(appUserRepository.existsByUpnIgnoreCase("never@example.com")).isFalse();
    }
}
