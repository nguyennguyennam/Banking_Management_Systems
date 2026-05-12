package com.bvb.accountmodule.service.DTO.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class UpdateAccountRequest {

    @NotNull
    @DecimalMin("1")
    private BigDecimal transactionLimit;

    @NotBlank
    private String changedBy;

    @NotBlank
    private String changedByRole;
}