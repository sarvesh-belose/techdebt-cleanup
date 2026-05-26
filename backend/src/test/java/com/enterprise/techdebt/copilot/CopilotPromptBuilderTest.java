package com.enterprise.techdebt.copilot;

import com.enterprise.techdebt.domain.Issue;
import com.enterprise.techdebt.domain.IssueSource;
import com.enterprise.techdebt.domain.IssueStatus;
import com.enterprise.techdebt.domain.Severity;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CopilotPromptBuilderTest {

    @Test
    void includesPreserveBusinessLogicInstruction() {
        FixRequest req = FixRequest.builder()
                .runId(42L)
                .workspacePath(Path.of("/tmp/ws"))
                .issues(List.of(sastIssue()))
                .attemptNumber(1)
                .build();
        String prompt = CopilotPromptBuilder.build(req);
        assertThat(prompt).contains("Do not change business logic");
        assertThat(prompt).contains("do not rewrite tests");
        assertThat(prompt).contains("VERACODE_SAST");
        assertThat(prompt).contains("CWE-79");
        assertThat(prompt).contains("src/main/java/Foo.java");
        assertThat(prompt).contains("git diff HEAD");
    }

    @Test
    void includesBuildErrorWhenRetrying() {
        FixRequest req = FixRequest.builder()
                .runId(1L)
                .workspacePath(Path.of("/tmp/ws"))
                .issues(List.of(sastIssue()))
                .previousBuildError("[ERROR] symbol not found: foo")
                .attemptNumber(2)
                .build();
        String prompt = CopilotPromptBuilder.build(req);
        assertThat(prompt).contains("previous attempt failed");
        assertThat(prompt).contains("symbol not found: foo");
    }

    @Test
    void scaIssueIncludesComponentAndSafeVersion() {
        FixRequest req = FixRequest.builder()
                .runId(1L)
                .workspacePath(Path.of("/tmp/ws"))
                .issues(List.of(scaIssue()))
                .attemptNumber(1)
                .build();
        String prompt = CopilotPromptBuilder.build(req);
        assertThat(prompt).contains("org.apache.logging.log4j:log4j-core");
        assertThat(prompt).contains("current: 2.14.0");
        assertThat(prompt).contains("safe: 2.17.1");
    }

    private Issue sastIssue() {
        Instant now = Instant.now();
        return Issue.builder()
                .id(1L)
                .projectId(1L)
                .source(IssueSource.VERACODE_SAST)
                .externalId("SAST-1")
                .severity(Severity.HIGH)
                .status(IssueStatus.OPEN)
                .title("XSS in render")
                .cwe("CWE-79")
                .filePath("src/main/java/Foo.java")
                .lineNumber(42)
                .firstSeen(now)
                .lastSeen(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Issue scaIssue() {
        Instant now = Instant.now();
        return Issue.builder()
                .id(2L)
                .projectId(1L)
                .source(IssueSource.VERACODE_SCA)
                .externalId("SCA-1")
                .severity(Severity.CRITICAL)
                .status(IssueStatus.OPEN)
                .title("CVE in log4j-core")
                .cve("CVE-2021-44228")
                .componentName("org.apache.logging.log4j:log4j-core")
                .currentVersion("2.14.0")
                .safeVersion("2.17.1")
                .isTransitive(false)
                .firstSeen(now)
                .lastSeen(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
