package com.enterprise.techdebt.azuredevops;

import com.enterprise.techdebt.workspace.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default Azure DevOps client for local dev — no network calls. Returns a canned
 * PR URL so the orchestrator can complete the happy path end-to-end without a real
 * tenant. Real impl lands in Sprint 6.
 */
@Component
@ConditionalOnProperty(name = "techdebt.azuredevops.client", havingValue = "noop", matchIfMissing = true)
public class NoopAzureDevOpsClient implements AzureDevOpsClient {

    private static final Logger log = LoggerFactory.getLogger(NoopAzureDevOpsClient.class);

    @Override
    public void pushFixBranch(Workspace workspace) {
        log.info("[noop] would git push origin {} (workspace {})", workspace.fixBranch(), workspace.path());
    }

    @Override
    public PullRequestRef openPullRequest(Workspace workspace,
                                           String title,
                                           String description,
                                           List<String> requiredReviewers) {
        String fakeUrl = workspace.repoUrl() + "/pullrequest/noop-" + workspace.runId();
        log.info("[noop] would open PR {} → {} with reviewers={}",
                workspace.fixBranch(), workspace.baseBranch(), requiredReviewers);
        return new PullRequestRef("noop-" + workspace.runId(), fakeUrl, title);
    }

    @Override
    public void mergePullRequest(PullRequestRef pr) {
        log.info("[noop] would merge PR {} ({})", pr.id(), pr.url());
    }
}
