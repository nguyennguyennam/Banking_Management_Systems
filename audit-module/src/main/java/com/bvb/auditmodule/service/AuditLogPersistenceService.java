package com.bvb.auditmodule.service;

import com.bvb.auditmodule.domain.AuditLog;
import com.bvb.auditmodule.repository.AuditLogRepository;
import com.bvb.sharedmodule.config.redis.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogPersistenceService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AuditLogEvent event) {
        try {
            AuditLog auditLog = AuditLog.from(event);
            auditLogRepository.save(auditLog);
            log.debug("Audit log persisted: entity={} action={} changedBy={}",
                    event.getEntityType(), event.getAction(), event.getChangedBy());
        } catch (Exception e) {
            log.error("Failed to persist audit log: entity={} action={}",
                    event.getEntityType(), event.getAction(), e);
        }
    }
}