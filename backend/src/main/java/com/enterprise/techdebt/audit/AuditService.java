package com.enterprise.techdebt.audit;

import com.enterprise.techdebt.config.MdcConstants;
import com.enterprise.techdebt.domain.AuditEvent;
import com.enterprise.techdebt.repo.AuditEventRepository;
import com.enterprise.techdebt.security.CurrentUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persist an audit event. Always runs in REQUIRES_NEW so a failed audit write
     * surfaces as its own error rather than rolling back the business transaction —
     * we want audit to be best-effort independent of feature success.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditEvent record(CurrentUser actor,
                              String action,
                              String resourceType,
                              String resourceId,
                              Long projectId,
                              Object before,
                              Object after,
                              String detail) {
        AuditEvent event = AuditEvent.builder()
                .occurredAt(Instant.now())
                .actorUserId(actor != null ? actor.userId() : null)
                .actorUpn(actor != null ? actor.upn() : null)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .projectId(projectId)
                .correlationId(MDC.get(MdcConstants.CORRELATION_ID))
                .beforeValue(toJson(before))
                .afterValue(toJson(after))
                .detail(detail)
                .build();
        AuditEvent saved = repository.save(event);
        log.debug("audit_event action={} resource={}:{} actor={}", action, resourceType, resourceId,
                actor != null ? actor.upn() : "system");
        return saved;
    }

    public AuditEvent record(CurrentUser actor, String action, String resourceType, String resourceId, Long projectId) {
        return record(actor, action, resourceType, resourceId, projectId, null, null, null);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit payload: {}", e.getMessage());
            return "\"<unserializable>\"";
        }
    }
}
