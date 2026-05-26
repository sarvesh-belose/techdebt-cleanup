package com.enterprise.techdebt.api.dto;

import com.enterprise.techdebt.domain.Notification;
import com.enterprise.techdebt.domain.Severity;
import com.enterprise.techdebt.notification.NotificationEventType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long userId,
        Long projectId,
        Long fixRunId,
        NotificationEventType eventType,
        Severity severity,
        String title,
        String body,
        String deeplink,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getUserId(),
                n.getProjectId(),
                n.getFixRunId(),
                n.getEventType(),
                n.getSeverity(),
                n.getTitle(),
                n.getBody(),
                n.getDeeplink(),
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}
