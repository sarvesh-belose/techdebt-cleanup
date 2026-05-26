package com.enterprise.techdebt.workspace;

import java.nio.file.Path;

public record Workspace(long runId, Path path, String repoUrl, String baseBranch, String fixBranch) {
}
