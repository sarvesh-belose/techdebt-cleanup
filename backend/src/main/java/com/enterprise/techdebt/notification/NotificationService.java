package com.enterprise.techdebt.notification;

import com.enterprise.techdebt.domain.Notification;
import com.enterprise.techdebt.domain.Severity;
import com.enterprise.techdebt.repo.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final List<NotificationDispatcher> dispatchers;

    public NotificationService(NotificationRepository repository,
                                List<NotificationDispatcher> dispatchers) {
        this.repository = repository;
        this.dispatchers = dispatchers;
    }

    /**
     * Persist an in-app inbox row + fan out to side channels (best-effort).
     * Returns the created notification so a test or caller can assert.
     */
    @Transactional
    public Notification notify(NotificationMessage message) {
        Notification n = Notification.builder()
                .userId(message.getUserId())
                .projectId(message.getProjectId())
                .fixRunId(message.getFixRunId())
                .eventType(message.getEventType())
                .severity(message.getSeverity() != null ? message.getSeverity() : Severity.INFO)
                .title(truncate(message.getTitle(), 500))
                .body(message.getBody())
                .deeplink(message.getDeeplink())
                .createdAt(Instant.now())
                .build();
        n = repository.save(n);

        for (NotificationDispatcher d : dispatchers) {
            try {
                d.send(message);
            } catch (RuntimeException ex) {
                log.warn("Dispatcher {} failed for {}: {}", d.channel(), message.getEventType(), ex.getMessage());
            }
        }
        return n;
    }

    @Transactional(readOnly = true)
    public Page<Notification> listForUser(Long userId, Pageable pageable) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countUnreadByUserId(userId);
    }

    @Transactional
    public Notification markRead(Long userId, Long notificationId) {
        Notification n = repository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        // Project-scoped notifications (userId null) can be marked read by any project member.
        if (n.getUserId() != null && !n.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Notification belongs to another user");
        }
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
            n = repository.save(n);
        }
        return n;
    }

    @Transactional
    public int markAllRead(Long userId) {
        List<Notification> unread = repository.findUnreadByUserId(userId);
        Instant now = Instant.now();
        for (Notification n : unread) {
            n.setReadAt(now);
        }
        repository.saveAll(unread);
        return unread.size();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
