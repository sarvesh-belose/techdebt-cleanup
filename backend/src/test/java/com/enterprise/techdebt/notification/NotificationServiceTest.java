package com.enterprise.techdebt.notification;

import com.enterprise.techdebt.domain.AppUser;
import com.enterprise.techdebt.domain.Notification;
import com.enterprise.techdebt.domain.Severity;
import com.enterprise.techdebt.repo.AppUserRepository;
import com.enterprise.techdebt.repo.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class NotificationServiceTest {

    @Autowired NotificationService service;
    @Autowired NotificationRepository repository;
    @Autowired AppUserRepository userRepository;

    private long user;

    @BeforeEach
    void clear() {
        repository.deleteAll();
        AppUser u = userRepository.findByUpnIgnoreCase("notif-test@local").orElseGet(() ->
                userRepository.save(AppUser.builder()
                        .upn("notif-test@local")
                        .email("notif-test@local")
                        .displayName("notif test")
                        .enabled(true)
                        .createdAt(Instant.now())
                        .build()));
        this.user = u.getId();
    }

    @Test
    void notifyPersistsInAppRowAndFansOutToDispatchers() {
        long countBefore = repository.count();
        Notification n = service.notify(NotificationMessage.builder()
                .eventType(NotificationEventType.PR_OPENED)
                .severity(Severity.INFO)
                .userId(user)
                .title("PR opened for run #1")
                .body("https://example/pr/1")
                .deeplink("https://example/pr/1")
                .build());
        assertThat(n.getId()).isNotNull();
        assertThat(repository.count()).isEqualTo(countBefore + 1);
        assertThat(n.getReadAt()).isNull();
    }

    @Test
    void unreadCountAndMarkRead() {
        service.notify(simple("a"));
        service.notify(simple("b"));
        service.notify(simple("c"));

        assertThat(service.unreadCount(user)).isEqualTo(3);
        Notification first = service.listForUser(user, PageRequest.of(0, 10)).getContent().get(0);
        service.markRead(user, first.getId());
        assertThat(service.unreadCount(user)).isEqualTo(2);
    }

    @Test
    void markAllReadSetsEveryUnread() {
        for (int i = 0; i < 5; i++) service.notify(simple("n" + i));
        int marked = service.markAllRead(user);
        assertThat(marked).isEqualTo(5);
        assertThat(service.unreadCount(user)).isZero();
    }

    @Test
    void markReadForAnotherUserIsRejected() {
        Notification n = service.notify(simple("private"));
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.markRead(user + 9999, n.getId()));
    }

    private NotificationMessage simple(String title) {
        return NotificationMessage.builder()
                .eventType(NotificationEventType.SCAN_COMPLETED)
                .severity(Severity.INFO)
                .userId(user)
                .title(title)
                .build();
    }
}
