package com.enterprise.techdebt.domain;

public enum FixRunState {
    QUEUED,
    CLONING,
    FIXING,
    FAST_BUILD,
    CANONICAL_BUILD,
    PR_OPENED,
    MERGED,
    FAILED,
    ABORTED,
    SAFETY_BLOCKED
}
