package com.bvb.auditmodule.config;

import com.bvb.auditmodule.service.AuditLogPersistenceService;
import com.bvb.sharedmodule.config.redis.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogSubscriber {

    private final AuditLogPersistenceService persistenceService;

    @Async("transferExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuditLogEvent(AuditLogEvent event) {
        try {
            persistenceService.persist(event);
        } catch (Exception e) {
            log.error("Failed to persist AuditLogEvent entity={} action={}",
                    event.getEntityType(), event.getAction(), e);
        }
    }
}
