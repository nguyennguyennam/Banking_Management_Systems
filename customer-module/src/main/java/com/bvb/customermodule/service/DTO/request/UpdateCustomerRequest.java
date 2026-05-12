package com.bvb.customermodule.service.DTO.request;

import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
public class UpdateCustomerRequest {

    @Size(max = 255)
    private String fullName;

    @Pattern(regexp = "^[0-9]{10,11}$", message = "Invalid phone number")
    private String phone;

    @Size(max = 255)
    private String address;

    @Size(max = 100)
    private String city;
}