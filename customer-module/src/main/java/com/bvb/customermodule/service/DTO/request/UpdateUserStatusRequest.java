package com.bvb.customermodule.service.DTO.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class UpdateUserStatusRequest {

    @NotNull(message = "Active status is required")
    private Boolean active;
}