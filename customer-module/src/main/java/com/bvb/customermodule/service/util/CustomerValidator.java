package com.bvb.customermodule.service.util;

import com.bvb.customermodule.repository.CustomerRepository;
import com.bvb.customermodule.repository.UserAuthRepository;
import com.bvb.customermodule.service.DTO.request.CreateCustomerRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomerValidator {

    private final CustomerRepository customerRepository;
    private final UserAuthRepository userAuthRepository;

    public void validateOnCreate(CreateCustomerRequest request) {
        if (customerRepository.existsCustomerByEmail(request.getEmail()))
            throw new IllegalArgumentException("Email already exists");
        if (customerRepository.existsCustomerByPhone(request.getPhone()))
            throw new IllegalArgumentException("Phone already exists");
        if (customerRepository.existsCustomerByNationalId(request.getNationalId()))
            throw new IllegalArgumentException("National ID already exists");
        if (userAuthRepository.findByUsername(request.getUsername()).isPresent())
            throw new IllegalArgumentException("Username already exists");
    }

    public void validateOnUpdate(UUID customerId, String phone) {
        if (phone != null &&
                customerRepository.existsCustomerByPhoneAndIdNot(phone, customerId))
            throw new IllegalArgumentException("Phone already exists");
    }
}