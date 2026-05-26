package com.enterprise.techdebt.azuredevops;

import com.enterprise.techdebt.workspace.Workspace;

import java.util.List;

/**
 * Operations against Azure DevOps used by the orchestrator: pushing the fix branch
 * and opening a PR. The canonical Azure Pipelines trigger and PR-status callbacks
 * are added in Sprint 6 (K1/K2).
 *
 * Implementations:
 *   - {@code noop} (default in local dev) returns a fake PR URL without touching the wire
 *   - {@code rest} uses Azure DevOps REST API + a service-principal PAT (skeleton, Sprint 6)
 */
public interface AzureDevOpsClient {

    /**
     * Push the fix branch to the configured remote. Implementations may rely on
     * git's existing remote config in the workspace.
     */
    void pushFixBranch(Workspace workspace);

    /**
     * Open a pull request from {@code workspace.fixBranch()} to {@code workspace.baseBranch()}.
     */
    PullRequestRef openPullRequest(Workspace workspace,
                                    String title,
                                    String description,
                                    List<String> requiredReviewers);

    /**
     * Auto-merge a PR opened by this platform. Implementations enforce server-side gates
     * (status checks, required reviewers) at the Azure DevOps API level; the orchestrator
     * additionally checks safety + auto-mode policy + rate limit before calling this.
     */
    void mergePullRequest(PullRequestRef pr);
}
