package com.bvb.accountmodule.service.DTO.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class AccountStatsResponse {

    private Map<String, Long> countByType;
    private long highBalance;
    private long mediumBalance;
    private long lowBalance;
}