package com.bvb.accountmodule.service.DTO.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class CreateAccountRequest {

    @NotNull
    private UUID customerId;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal initialBalance;

    @NotBlank
    private String changedBy;

    @NotBlank
    private String changedByRole;
}