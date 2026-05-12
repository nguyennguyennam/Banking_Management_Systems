package com.bvb.auditmodule.service.DTO.request;

import com.bvb.sharedmodule.config.redis.AuditLogEvent;
import lombok.Getter;

import java.util.UUID;

@Getter
public class AuditEntitySearchRequest {
    private AuditLogEvent.EntityType entityType;
    private UUID entityId;   // optional — nếu null thì chỉ filter theo entityType
    private int  page    = 0;
    private int  size    = 20;
}