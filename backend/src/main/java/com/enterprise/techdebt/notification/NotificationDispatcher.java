package com.enterprise.techdebt.notification;

/**
 * Side-channel notification delivery (Teams, email). The in-app channel is handled
 * directly by {@link NotificationService} writing to the notification table.
 *
 * Dispatchers run best-effort: a Teams 5xx never blocks an audit/state write.
 */
public interface NotificationDispatcher {
    NotificationChannel channel();

    void send(NotificationMessage message);
}
