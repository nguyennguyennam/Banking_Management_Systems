package com.bvb.auditmodule.service.DTO.request;

import lombok.Getter;

@Getter
public class AuditSearchRequest {
    private final int page     = 0;
    private final int size     = 20;
    private final String sortBy  = "changedAt";
    private final String sortDir = "desc";
}