package com.enterprise.techdebt.azuredevops;

import com.enterprise.techdebt.workspace.Workspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Production Azure DevOps client — skeleton. Activated when
 * {@code techdebt.azuredevops.client=rest}.
 *
 * Wiring (Sprint 6):
 *  - Resolve PAT via SecretStore (one secret per organization, rotated quarterly).
 *  - Push: configure git remote with PAT-authenticated URL form
 *      https://{org}:{PAT}@dev.azure.com/{org}/{project}/_git/{repo}
 *    and shell out via {@code GitRunner}. Force-push is BLOCKED once a PR exists; the
 *    workspace records PR state so the orchestrator can choose update-branch over
 *    force-push during refix.
 *  - PR open: POST
 *      https://dev.azure.com/{org}/{project}/_apis/git/repositories/{repoId}/pullrequests?api-version=7.0
 *    Body:
 *      {
 *        "sourceRefName": "refs/heads/{fixBranch}",
 *        "targetRefName": "refs/heads/{baseBranch}",
 *        "title": ..., "description": ...,
 *        "reviewers": [{"id": "..."}]
 *      }
 *  - PR description: render Markdown that includes the issue list, safety score,
 *    audit-log deep link, and the run id (so a reviewer can trace back to the platform).
 *  - Pipeline trigger (K1) lives in a separate class, AzurePipelinesRunner, to keep
 *    concerns split: PRs are git/REST, pipelines are a different REST surface.
 */
@Component
@ConditionalOnProperty(name = "techdebt.azuredevops.client", havingValue = "rest")
public class AzureDevOpsRestClient implements AzureDevOpsClient {

    private static final Logger log = LoggerFactory.getLogger(AzureDevOpsRestClient.class);

    public AzureDevOpsRestClient() {
        log.warn("AzureDevOpsRestClient is a skeleton. Implement push + PR REST calls before non-local use.");
    }

    @Override
    public void pushFixBranch(Workspace workspace) {
        throw new UnsupportedOperationException("AzureDevOpsRestClient.pushFixBranch not implemented yet");
    }

    @Override
    public PullRequestRef openPullRequest(Workspace workspace, String title, String description,
                                           List<String> requiredReviewers) {
        throw new UnsupportedOperationException("AzureDevOpsRestClient.openPullRequest not implemented yet");
    }

    @Override
    public void mergePullRequest(PullRequestRef pr) {
        // Production wiring: PATCH /pullrequests/{id} with body
        //   { "status": "completed", "lastMergeSourceCommit": {...}, "completionOptions": {...} }
        throw new UnsupportedOperationException("AzureDevOpsRestClient.mergePullRequest not implemented yet");
    }
}
