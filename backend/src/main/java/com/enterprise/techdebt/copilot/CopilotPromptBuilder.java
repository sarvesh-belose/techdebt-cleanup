package com.enterprise.techdebt.copilot;

import com.enterprise.techdebt.domain.Issue;
import com.enterprise.techdebt.domain.IssueSource;

import java.util.StringJoiner;

/**
 * Builds the structured prompt fed to {@code gh copilot suggest}. The template is
 * deliberately constrained: the LLM is told to make the smallest possible change
 * that fixes the named issue without altering business logic, and it is told NOT to
 * rewrite tests for the changed method.
 */
public final class CopilotPromptBuilder {

    private CopilotPromptBuilder() {}

    public static String build(FixRequest req) {
        StringJoiner findings = new StringJoiner("\n\n");
        for (Issue issue : req.getIssues()) {
            findings.add(renderIssue(issue));
        }

        StringBuilder sb = new StringBuilder(2048);
        sb.append("You are fixing tech-debt findings in a Java Spring Boot service. Apply the smallest")
          .append(" possible change that resolves the listed issue(s). Do not change business logic;")
          .append(" do not rewrite tests for changed methods; do not reformat unrelated code.")
          .append("\n\n")
          .append("Findings:\n")
          .append(findings)
          .append("\n");
        if (req.getPreviousBuildError() != null && !req.getPreviousBuildError().isBlank()) {
            sb.append("\nThe previous attempt failed to build. Build error excerpt (last ~200 lines):\n")
              .append(truncate(req.getPreviousBuildError(), 8000))
              .append("\n");
        }
        sb.append("\nMake the edits directly in the working directory. After editing, the tool will")
          .append(" capture `git diff HEAD` as the canonical change record. Do not commit.\n");
        return sb.toString();
    }

    private static String renderIssue(Issue issue) {
        StringBuilder b = new StringBuilder();
        b.append("- ").append(issue.getSource()).append(" / ").append(issue.getSeverity())
                .append(": ").append(issue.getTitle()).append("\n");
        if (issue.getFilePath() != null) {
            b.append("  file: ").append(issue.getFilePath());
            if (issue.getLineNumber() != null) b.append(":").append(issue.getLineNumber());
            b.append("\n");
        }
        if (issue.getCwe() != null) b.append("  cwe: ").append(issue.getCwe()).append("\n");
        if (issue.getCve() != null) b.append("  cve: ").append(issue.getCve()).append("\n");
        if (issue.getSource() == IssueSource.VERACODE_SCA) {
            if (issue.getComponentName() != null) b.append("  component: ").append(issue.getComponentName()).append("\n");
            if (issue.getCurrentVersion() != null) b.append("  current: ").append(issue.getCurrentVersion()).append("\n");
            if (issue.getSafeVersion() != null) b.append("  safe: ").append(issue.getSafeVersion()).append("\n");
            if (Boolean.TRUE.equals(issue.getIsTransitive())) b.append("  transitive: true\n");
        }
        if (issue.getDescription() != null) {
            b.append("  description: ").append(truncate(issue.getDescription(), 600)).append("\n");
        }
        return b.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + " …(truncated)";
    }
}
