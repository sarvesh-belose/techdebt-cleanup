package com.enterprise.techdebt.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Posts notifications to a Teams incoming-webhook URL as an Adaptive Card. The webhook
 * is org-wide for now (configured via {@code techdebt.notification.teams.webhook-url});
 * per-project routing arrives later by adding a {@code teams_webhook_url} column on
 * project and looking it up per message.
 *
 * Activated when {@code techdebt.notification.teams.enabled=true}. Default is false so
 * local dev doesn't accidentally spam a channel.
 */
@Component
@ConditionalOnProperty(name = "techdebt.notification.teams.enabled", havingValue = "true")
public class TeamsWebhookDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(TeamsWebhookDispatcher.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String webhookUrl;

    public TeamsWebhookDispatcher(ObjectMapper objectMapper,
                                  @Value("${techdebt.notification.teams.webhook-url:}") String webhookUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = objectMapper;
        this.webhookUrl = webhookUrl;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.TEAMS;
    }

    @Override
    public void send(NotificationMessage message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Teams webhook not configured — skipping {}", message.getEventType());
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(buildAdaptiveCard(message));
            HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Teams webhook returned HTTP {} for {}", resp.statusCode(), message.getEventType());
            }
        } catch (Exception e) {
            log.warn("Teams webhook delivery failed for {}: {}", message.getEventType(), e.getMessage());
        }
    }

    private Map<String, Object> buildAdaptiveCard(NotificationMessage m) {
        // MessageCard format — universally supported by Teams incoming webhooks.
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("@type", "MessageCard");
        card.put("@context", "https://schema.org/extensions");
        card.put("themeColor", themeColor(m));
        card.put("summary", m.getTitle());
        card.put("title", m.getTitle());
        card.put("text", m.getBody() == null ? "" : m.getBody());
        if (m.getDeeplink() != null) {
            Map<String, Object> action = new LinkedHashMap<>();
            action.put("@type", "OpenUri");
            action.put("name", "Open in TechDebt Cleanup");
            action.put("targets", java.util.List.of(Map.of("os", "default", "uri", m.getDeeplink())));
            card.put("potentialAction", java.util.List.of(action));
        }
        return card;
    }

    private String themeColor(NotificationMessage m) {
        return switch (m.getSeverity()) {
            case CRITICAL -> "d13438";
            case HIGH -> "f08c00";
            case MEDIUM -> "ffaa44";
            case LOW -> "5cb85c";
            default -> "5bc0de";
        };
    }
}
