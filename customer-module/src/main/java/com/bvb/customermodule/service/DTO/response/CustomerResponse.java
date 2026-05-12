package com.bvb.customermodule.service.DTO.response;

import com.bvb.customermodule.domain.CustomerType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class CustomerResponse {

    private UUID id;
    private CustomerType.TypeName customerType;
    private String fullName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String nationalId;
    private Instant createdAt;
    private Instant updatedAt;
}