package com.enterprise.techdebt.config;

public final class MdcConstants {
    public static final String CORRELATION_ID = "correlation_id";
    public static final String USER_ID = "user_id";
    public static final String USER_UPN = "user_upn";
    public static final String PROJECT_ID = "project_id";
    public static final String RUN_ID = "run_id";

    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    private MdcConstants() {}
}
