package com.enterprise.techdebt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Surfaces runtime configuration on {@code /actuator/info} so SRE can answer
 * "what is actually running?" without log-trawling. Deliberately omits secret values —
 * only flags + impl names that are safe to expose. Sits alongside the build-info that
 * spring-boot-maven-plugin writes (version, time, group).
 */
@Component
public class RuntimeInfoContributor implements InfoContributor {

    private final Environment env;
    private final boolean devBypass;
    private final String secretStoreImpl;
    private final String fixGenerator;
    private final String azureDevOpsClient;
    private final String behaviorOpiner;
    private final int maxRefixAttempts;
    private final boolean schedulerEnabled;
    private final boolean retentionEnabled;

    public RuntimeInfoContributor(Environment env,
                                  @Value("${techdebt.security.dev-bypass:false}") boolean devBypass,
                                  @Value("${techdebt.secretstore.impl:in-memory}") String secretStoreImpl,
                                  @Value("${techdebt.copilot.generator:noop}") String fixGenerator,
                                  @Value("${techdebt.azuredevops.client:noop}") String azureDevOpsClient,
                                  @Value("${techdebt.safety.behavior-opiner:noop}") String behaviorOpiner,
                                  @Value("${techdebt.orchestrator.max-refix-attempts:3}") int maxRefixAttempts,
                                  @Value("${techdebt.scheduler.enabled:true}") boolean schedulerEnabled,
                                  @Value("${techdebt.retention.enabled:true}") boolean retentionEnabled) {
        this.env = env;
        this.devBypass = devBypass;
        this.secretStoreImpl = secretStoreImpl;
        this.fixGenerator = fixGenerator;
        this.azureDevOpsClient = azureDevOpsClient;
        this.behaviorOpiner = behaviorOpiner;
        this.maxRefixAttempts = maxRefixAttempts;
        this.schedulerEnabled = schedulerEnabled;
        this.retentionEnabled = retentionEnabled;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("activeProfiles", List.of(env.getActiveProfiles()));
        runtime.put("devBypass", devBypass);
        runtime.put("secretStoreImpl", secretStoreImpl);
        runtime.put("fixGenerator", fixGenerator);
        runtime.put("azureDevOpsClient", azureDevOpsClient);
        runtime.put("behaviorOpiner", behaviorOpiner);
        runtime.put("maxRefixAttempts", maxRefixAttempts);
        runtime.put("schedulerEnabled", schedulerEnabled);
        runtime.put("retentionEnabled", retentionEnabled);
        runtime.put("javaVersion", System.getProperty("java.version"));
        // No secret values — by policy, nothing under techdebt.secretstore.keyvault.* or similar.
        builder.withDetail("techdebt", runtime);
    }
}
