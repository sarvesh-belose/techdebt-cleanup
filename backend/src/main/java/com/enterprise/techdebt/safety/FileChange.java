package com.enterprise.techdebt.safety;

import lombok.Value;

/**
 * One changed file with before/after content. Either side may be null for added/deleted files.
 */
@Value
public class FileChange {
    String path;
    String beforeContent;
    String afterContent;

    public boolean isAdded() {
        return beforeContent == null && afterContent != null;
    }

    public boolean isDeleted() {
        return beforeContent != null && afterContent == null;
    }

    public boolean isJava() {
        return path != null && path.endsWith(".java");
    }

    public boolean isPom() {
        return path != null && (path.equals("pom.xml") || path.endsWith("/pom.xml"));
    }

    public boolean isTest() {
        return path != null && path.contains("/src/test/");
    }

    public boolean isMain() {
        return path != null && path.contains("/src/main/");
    }
}
