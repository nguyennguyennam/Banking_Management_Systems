package com.bvb.transactionmodule.service.DTO.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class RollbackRequest {

    @NotBlank
    private String reason;
}