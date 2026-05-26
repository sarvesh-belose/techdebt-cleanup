package com.enterprise.techdebt.notification;

import com.enterprise.techdebt.domain.Severity;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class NotificationMessage {
    NotificationEventType eventType;
    Severity severity;
    Long projectId;
    Long fixRunId;
    /** Optional explicit target user. If null, notification is project-scoped. */
    Long userId;
    String title;
    String body;
    String deeplink;
}
