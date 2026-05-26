package com.enterprise.techdebt.domain;

public enum BuildOutcome {
    PENDING,
    SUCCESS,
    COMPILE_FAILURE,
    TEST_FAILURE,
    TIMEOUT,
    INFRA_ERROR
}
