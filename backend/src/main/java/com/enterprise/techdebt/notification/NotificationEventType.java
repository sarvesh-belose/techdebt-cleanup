package com.enterprise.techdebt.notification;

public enum NotificationEventType {
    SCAN_COMPLETED,
    SCAN_FAILED,
    FIX_RUN_CREATED,
    PR_OPENED,
    MERGED,
    SAFETY_BLOCKED,
    RUN_FAILED,
    AUTO_MODE_KILL_SWITCH_TOGGLED
}
