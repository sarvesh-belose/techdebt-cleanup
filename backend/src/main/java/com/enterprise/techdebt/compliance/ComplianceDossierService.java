package com.enterprise.techdebt.compliance;

import com.enterprise.techdebt.domain.AuditEvent;
import com.enterprise.techdebt.domain.FixAttempt;
import com.enterprise.techdebt.domain.FixRun;
import com.enterprise.techdebt.domain.Issue;
import com.enterprise.techdebt.domain.Project;
import com.enterprise.techdebt.repo.AuditEventRepository;
import com.enterprise.techdebt.repo.FixAttemptRepository;
import com.enterprise.techdebt.repo.IssueRepository;
import com.enterprise.techdebt.repo.ProjectRepository;
import com.enterprise.techdebt.orchestrator.FixRunService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a self-contained HTML dossier for a single Fix Run — the artefact InfoSec
 * downloads when they ask "show me everything about this PR."
 *
 * HTML for now to keep the dependency footprint minimal; a follow-up adds PDF via
 * openhtml-flying-saucer when the platform's static assets get versioned. The HTML
 * here is already print-friendly so a browser "Save as PDF" produces a usable result.
 */
@Service
public class ComplianceDossierService {

    private final FixRunService fixRunService;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;
    private final FixAttemptRepository attemptRepository;
    private final AuditEventRepository auditEventRepository;

    public ComplianceDossierService(FixRunService fixRunService,
                                    ProjectRepository projectRepository,
                                    IssueRepository issueRepository,
                                    FixAttemptRepository attemptRepository,
                                    AuditEventRepository auditEventRepository) {
        this.fixRunService = fixRunService;
        this.projectRepository = projectRepository;
        this.issueRepository = issueRepository;
        this.attemptRepository = attemptRepository;
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public String renderHtml(long projectId, long runId) {
        FixRun run = fixRunService.get(runId);
        if (!run.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Run " + runId + " is not part of project " + projectId);
        }
        Project project = projectRepository.findById(projectId).orElse(null);
        List<FixAttempt> attempts = attemptRepository.findAllByFixRunIdOrderByAttemptNumberAsc(runId);
        List<Issue> issues = loadIssues(run);
        List<AuditEvent> auditEvents = auditEventRepository
                .findAllByProjectId(projectId, PageRequest.of(0, 200))
                .stream()
                .filter(e -> runIdMatches(e, runId))
                .toList();

        StringBuilder sb = new StringBuilder(8192);
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        sb.append("<title>Fix Run ").append(runId).append(" Dossier</title>");
        sb.append("<style>")
          .append("body{font-family:-apple-system,Segoe UI,Roboto,sans-serif;color:#1f2937;margin:24px;}")
          .append("h1,h2{color:#0f172a;border-bottom:1px solid #e5e7eb;padding-bottom:4px}")
          .append("table{border-collapse:collapse;width:100%;margin-bottom:16px}")
          .append("th,td{border:1px solid #e5e7eb;padding:6px 10px;text-align:left;font-size:13px}")
          .append("th{background:#f9fafb}")
          .append("pre{background:#f1f5f9;padding:10px;border-radius:4px;font-size:12px;white-space:pre-wrap;}")
          .append(".tag{display:inline-block;padding:2px 8px;border-radius:9999px;background:#dbeafe;color:#1e3a8a;font-size:12px;}")
          .append(".muted{color:#64748b;font-size:12px}")
          .append("@media print{body{margin:0.5in}}")
          .append("</style></head><body>");

        sb.append("<h1>Fix Run #").append(runId).append("</h1>");
        sb.append("<p class=\"muted\">Generated ").append(Instant.now()).append("</p>");

        sb.append("<h2>Project</h2><table>");
        kv(sb, "Name", project != null ? project.getName() : "(unknown)");
        kv(sb, "Repo", project != null ? project.getAzureRepoUrl() : "(unknown)");
        kv(sb, "Default branch", project != null ? project.getDefaultBranch() : "(unknown)");
        kv(sb, "Safety threshold", project != null ? String.valueOf(project.getSafetyThreshold()) : "?");
        kv(sb, "Auto-mode", project != null && project.isAutoModeEnabled() ? "ON" : "off");
        sb.append("</table>");

        sb.append("<h2>Run</h2><table>");
        kv(sb, "State", run.getState().name());
        kv(sb, "Initiated by user", String.valueOf(run.getInitiatedByUserId()));
        kv(sb, "Auto mode", String.valueOf(run.isAutoMode()));
        kv(sb, "Branch", nz(run.getBranchName()));
        kv(sb, "Commit", nz(run.getCommitSha()));
        kv(sb, "PR URL", nz(run.getPrUrl()));
        kv(sb, "Safety score", run.getSafetyScore() != null ? run.getSafetyScore() + "/100" : "—");
        kv(sb, "Safety reason", nz(run.getSafetyBlockedReason()));
        kv(sb, "Failure reason", nz(run.getFailureReason()));
        kv(sb, "Created", String.valueOf(run.getCreatedAt()));
        kv(sb, "Completed", run.getCompletedAt() != null ? run.getCompletedAt().toString() : "—");
        sb.append("</table>");

        sb.append("<h2>Issues fixed (").append(issues.size()).append(")</h2><table>");
        sb.append("<tr><th>ID</th><th>Source</th><th>Severity</th><th>Title</th><th>File / Component</th></tr>");
        for (Issue i : issues) {
            sb.append("<tr>")
              .append("<td>").append(i.getId()).append("</td>")
              .append("<td>").append(i.getSource()).append("</td>")
              .append("<td>").append(i.getSeverity()).append("</td>")
              .append("<td>").append(escape(i.getTitle())).append("</td>")
              .append("<td>").append(escape(locationOf(i))).append("</td>")
              .append("</tr>");
        }
        sb.append("</table>");

        sb.append("<h2>Attempts (").append(attempts.size()).append(")</h2>");
        for (FixAttempt a : attempts) {
            sb.append("<h3>Attempt #").append(a.getAttemptNumber()).append("</h3><table>");
            kv(sb, "Build outcome", a.getBuildOutcome() != null ? a.getBuildOutcome().name() : "—");
            kv(sb, "Safety score", a.getSafetyScore() != null ? a.getSafetyScore() + "/100" : "—");
            kv(sb, "Started", String.valueOf(a.getStartedAt()));
            kv(sb, "Completed", a.getCompletedAt() != null ? a.getCompletedAt().toString() : "—");
            sb.append("</table>");
            if (a.getSafetySignalsJson() != null && !a.getSafetySignalsJson().isBlank()) {
                sb.append("<div class=\"tag\">Safety signals</div><pre>")
                  .append(escape(a.getSafetySignalsJson())).append("</pre>");
            }
            if (a.getBuildLogExcerpt() != null && !a.getBuildLogExcerpt().isBlank()) {
                sb.append("<div class=\"tag\">Build log tail</div><pre>")
                  .append(escape(a.getBuildLogExcerpt())).append("</pre>");
            }
            if (a.getDiff() != null && !a.getDiff().isBlank()) {
                sb.append("<div class=\"tag\">Diff</div><pre>")
                  .append(escape(truncate(a.getDiff(), 30000))).append("</pre>");
            }
        }

        sb.append("<h2>Audit trail (this run, scoped to this project)</h2>");
        if (auditEvents.isEmpty()) {
            sb.append("<p class=\"muted\">No audit events recorded.</p>");
        } else {
            sb.append("<table><tr><th>When</th><th>Actor</th><th>Action</th><th>Resource</th><th>Detail</th></tr>");
            for (AuditEvent e : auditEvents) {
                sb.append("<tr>")
                  .append("<td>").append(e.getOccurredAt()).append("</td>")
                  .append("<td>").append(escape(nz(e.getActorUpn()))).append("</td>")
                  .append("<td>").append(e.getAction()).append("</td>")
                  .append("<td>").append(e.getResourceType()).append("/").append(nz(e.getResourceId())).append("</td>")
                  .append("<td>").append(escape(nz(e.getDetail()))).append("</td>")
                  .append("</tr>");
            }
            sb.append("</table>");
        }

        sb.append("<p class=\"muted\">End of dossier · ").append(Instant.now()).append("</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private List<Issue> loadIssues(FixRun run) {
        if (run.getIssueIdsJson() == null || run.getIssueIdsJson().isBlank()) return List.of();
        String trimmed = run.getIssueIdsJson().trim()
                .replaceAll("^\\[", "").replaceAll("]$", "");
        List<Issue> out = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            try {
                long id = Long.parseLong(t);
                issueRepository.findById(id).ifPresent(out::add);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private boolean runIdMatches(AuditEvent e, long runId) {
        if ("FIX_RUN".equals(e.getResourceType()) && String.valueOf(runId).equals(e.getResourceId())) {
            return true;
        }
        String runStr = "run " + runId;
        if (e.getAfterValue() != null && e.getAfterValue().contains(runStr)) return true;
        return e.getDetail() != null && e.getDetail().contains(runStr);
    }

    private String locationOf(Issue i) {
        if (i.getFilePath() != null) {
            return i.getFilePath() + (i.getLineNumber() != null ? ":" + i.getLineNumber() : "");
        }
        if (i.getComponentName() != null) {
            return i.getComponentName()
                    + (i.getCurrentVersion() != null ? " " + i.getCurrentVersion() : "")
                    + (i.getSafeVersion() != null ? " → " + i.getSafeVersion() : "");
        }
        return "—";
    }

    private static void kv(StringBuilder sb, String label, String value) {
        sb.append("<tr><th>").append(label).append("</th><td>")
          .append(escape(value == null ? "—" : value))
          .append("</td></tr>");
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String nz(String s) {
        return s == null ? "—" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "\n… (truncated)";
    }
}
