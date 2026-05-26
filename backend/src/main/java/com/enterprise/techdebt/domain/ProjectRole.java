package com.enterprise.techdebt.domain;

public enum ProjectRole {
    VIEWER,
    DEVELOPER,
    APPROVER,
    ADMIN;

    public boolean atLeast(ProjectRole required) {
        return this.ordinal() >= required.ordinal();
    }
}
