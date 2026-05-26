package com.enterprise.techdebt.maven;

import lombok.Value;

@Value
public class PomFixResult {
    boolean changed;
    String groupId;
    String artifactId;
    String previousVersion;
    String newVersion;
    /** What we touched: "dependency.version" or "property:{name}". */
    String mechanism;
    String notes;
}
