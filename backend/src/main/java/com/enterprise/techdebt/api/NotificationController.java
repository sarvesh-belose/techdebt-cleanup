package com.enterprise.techdebt.api;

import com.enterprise.techdebt.api.dto.NotificationResponse;
import com.enterprise.techdebt.api.dto.PageResponse;
import com.enterprise.techdebt.notification.NotificationService;
import com.enterprise.techdebt.security.CurrentUserService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(@PageableDefault(size = 50) Pageable pageable) {
        var actor = currentUserService.require();
        return PageResponse.of(service.listForUser(actor.userId(), pageable), NotificationResponse::from);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        var actor = currentUserService.require();
        return Map.of("unread", service.unreadCount(actor.userId()));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id) {
        var actor = currentUserService.require();
        return NotificationResponse.from(service.markRead(actor.userId(), id));
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead() {
        var actor = currentUserService.require();
        return Map.of("marked", service.markAllRead(actor.userId()));
    }
}
