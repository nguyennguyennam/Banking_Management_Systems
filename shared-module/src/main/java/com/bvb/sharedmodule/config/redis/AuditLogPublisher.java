package com.bvb.sharedmodule.config.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public void publish(AuditLogEvent event) {
        eventPublisher.publishEvent(event);
        log.debug("Published AuditLogEvent: entity={} action={}",
                event.getEntityType(), event.getAction());
    }
}
