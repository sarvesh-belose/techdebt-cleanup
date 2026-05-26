package com.enterprise.techdebt.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Email dispatcher — skeleton. Activated when
 * {@code techdebt.notification.email.enabled=true}. Production wiring should use
 * spring-boot-starter-mail with an SMTP relay configured per environment.
 *
 * Kept as a stub so the dispatcher list isn't empty when only Teams is configured.
 */
@Component
@ConditionalOnProperty(name = "techdebt.notification.email.enabled", havingValue = "true")
public class EmailDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatcher.class);

    public EmailDispatcher() {
        log.warn("EmailDispatcher is a stub. Wire spring-boot-starter-mail before non-local use.");
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(NotificationMessage message) {
        log.info("[email-stub] would send '{}' for run={}", message.getTitle(), message.getFixRunId());
    }
}
