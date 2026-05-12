package com.bvb.customermodule.service.DTO.request;

import com.bvb.customermodule.domain.CustomerType;
import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
public class CreateCustomerRequest {

    @NotNull(message = "Customer type is required")
    private CustomerType.TypeName customerType;

    @NotBlank(message = "Full name is required")
    @Size(max = 255)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[0-9]{10,11}$", message = "Invalid phone number")
    private String phone;

    @NotBlank(message = "Address is required")
    @Size(max = 255)
    private String address;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @NotBlank(message = "National ID is required")
    @Size(max = 20)
    private String nationalId;

    // Credentials
    @NotBlank(message = "Username is required")
    @Size(max = 255)
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}