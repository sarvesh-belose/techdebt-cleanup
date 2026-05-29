package com.enterprise.techdebt.dev;

import com.enterprise.techdebt.domain.IssueSource;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code dev.config.yml} (imported via {@code spring.config.import} on the
 * local profile). When {@link #isEnabled()} is true, {@link DevBootstrapRunner}
 * applies each project + scan source on startup.
 *
 * Bound from {@code techdebt.dev.bootstrap.*} so values can reference env vars
 * via Spring's {@code ${VAR:default}} placeholder syntax — convenient for
 * shared dev machines that don't want secrets in a checked-out file.
 */
@ConfigurationProperties(prefix = "techdebt.dev.bootstrap")
public class DevBootstrapProperties {

    private boolean enabled = false;
    private String copilotGenerator = "noop";
    private List<ProjectConfig> projects = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getCopilotGenerator() { return copilotGenerator; }
    public void setCopilotGenerator(String copilotGenerator) { this.copilotGenerator = copilotGenerator; }
    public List<ProjectConfig> getProjects() { return projects; }
    public void setProjects(List<ProjectConfig> projects) { this.projects = projects; }

    public static class ProjectConfig {
        private String name;
        private String repoUrl;
        private String defaultBranch = "main";
        private String description;
        private boolean triggerInitialScan;
        private List<ScanSourceConfig> scanSources = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRepoUrl() { return repoUrl; }
        public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
        public String getDefaultBranch() { return defaultBranch; }
        public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isTriggerInitialScan() { return triggerInitialScan; }
        public void setTriggerInitialScan(boolean triggerInitialScan) { this.triggerInitialScan = triggerInitialScan; }
        public List<ScanSourceConfig> getScanSources() { return scanSources; }
        public void setScanSources(List<ScanSourceConfig> scanSources) { this.scanSources = scanSources; }
    }

    public static class ScanSourceConfig {
        private IssueSource source;
        private String externalProjectId;
        private String apiBaseUrl;
        private String secret;
        private String cronExpression;
        private Integer coverageThresholdPct;

        public IssueSource getSource() { return source; }
        public void setSource(IssueSource source) { this.source = source; }
        public String getExternalProjectId() { return externalProjectId; }
        public void setExternalProjectId(String externalProjectId) { this.externalProjectId = externalProjectId; }
        public String getApiBaseUrl() { return apiBaseUrl; }
        public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getCronExpression() { return cronExpression; }
        public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
        public Integer getCoverageThresholdPct() { return coverageThresholdPct; }
        public void setCoverageThresholdPct(Integer coverageThresholdPct) { this.coverageThresholdPct = coverageThresholdPct; }
    }
}
