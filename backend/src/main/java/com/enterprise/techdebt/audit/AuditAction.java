package com.enterprise.techdebt.audit;

public final class AuditAction {
    public static final String PROJECT_CREATED = "PROJECT_CREATED";
    public static final String PROJECT_UPDATED = "PROJECT_UPDATED";
    public static final String PROJECT_ARCHIVED = "PROJECT_ARCHIVED";
    public static final String PROJECT_UNARCHIVED = "PROJECT_UNARCHIVED";

    public static final String SCAN_SOURCE_CONFIGURED = "SCAN_SOURCE_CONFIGURED";
    public static final String SCAN_TRIGGERED = "SCAN_TRIGGERED";
    public static final String SCAN_COMPLETED = "SCAN_COMPLETED";
    public static final String SCAN_FAILED = "SCAN_FAILED";

    public static final String ISSUE_STATUS_CHANGED = "ISSUE_STATUS_CHANGED";

    public static final String FIX_RUN_CREATED = "FIX_RUN_CREATED";
    public static final String FIX_RUN_STATE_CHANGED = "FIX_RUN_STATE_CHANGED";
    public static final String FIX_RUN_SAFETY_OVERRIDE = "FIX_RUN_SAFETY_OVERRIDE";

    public static final String PERMISSION_GRANTED = "PERMISSION_GRANTED";
    public static final String PERMISSION_REVOKED = "PERMISSION_REVOKED";

    public static final String AUTO_MODE_TOGGLED = "AUTO_MODE_TOGGLED";

    private AuditAction() {}
}
