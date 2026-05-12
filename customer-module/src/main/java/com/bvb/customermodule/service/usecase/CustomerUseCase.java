package com.bvb.customermodule.service.usecase;

import com.bvb.customermodule.domain.Customer;
import com.bvb.customermodule.domain.CustomerType;
import com.bvb.customermodule.domain.UserAuth;
import com.bvb.customermodule.repository.CustomerRepository;
import com.bvb.customermodule.repository.CustomerTypeRepository;
import com.bvb.customermodule.repository.UserAuthRepository;
import com.bvb.customermodule.service.DTO.request.CreateCustomerRequest;
import com.bvb.customermodule.service.DTO.request.UpdateCustomerRequest;
import com.bvb.customermodule.service.DTO.response.CustomerResponse;
import com.bvb.customermodule.service.util.CustomerMapper;
import com.bvb.customermodule.service.util.CustomerValidator;
import com.bvb.sharedmodule.config.redis.AuditLogEvent;
import com.bvb.sharedmodule.config.redis.AuditLogPublisher;
import com.bvb.sharedmodule.service.response.PageResponse;
import com.bvb.sharedmodule.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerUseCase {

    private final CustomerRepository customerRepository;
    private final CustomerTypeRepository customerTypeRepository;
    private final UserAuthRepository userAuthRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomerValidator customerValidator;
    private final CustomerMapper customerMapper;
    private final AuditLogPublisher auditLogPublisher;
    private final ObjectMapper objectMapper;

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        customerValidator.validateOnCreate(request);

        CustomerType customerType = customerTypeRepository
                .findByTypeName(request.getCustomerType())
                .orElseThrow(() -> new IllegalArgumentException("Invalid customer type"));

        Customer customer = Customer.builder()
                .id(UUID.randomUUID())
                .customerType(customerType)
                .fullName(request.getFullName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .nationalId(request.getNationalId())
                .build();
        customerRepository.save(customer);

        UserAuth userAuth = UserAuth.builder()
                .id(UUID.randomUUID())
                .customerId(customer.getId())
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .active(true)
                .build();
        userAuthRepository.save(userAuth);

        auditLogPublisher.publish(AuditLogEvent.builder()
                .entityId(customer.getId())
                .entityType(AuditLogEvent.EntityType.CUSTOMER)
                .action(AuditLogEvent.Action.CREATE)
                .newValue(toJson(customer))
                .changedBy(request.getUsername())
                .changedByRole(Role.ADMIN.name())
                .occurredAt(Instant.now())
                .build());

        log.info("Customer created: {}", customer.getId());
        return customerMapper.toResponse(customer);
    }

    // ── READ ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        return customerMapper.toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> getAll(Pageable pageable) {
        return PageResponse.from(
                customerRepository.findAll(pageable).map(customerMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> searchByName(String name, Pageable pageable) {
        return PageResponse.from(
                customerRepository.searchByName(name, pageable).map(customerMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> getByCity(String city, Pageable pageable) {
        return PageResponse.from(
                customerRepository.findByCity(city, pageable).map(customerMapper::toResponse));
    }

    // ── STATS ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Long> countByCity() {
        return customerRepository.countCustomersByCity().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countByType() {
        return customerRepository.countByCustomerType().stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1]
                ));
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Transactional
    public CustomerResponse updateProfile(UUID customerId,
                                          UpdateCustomerRequest request,
                                          UserAuth currentUser) {
        if (currentUser.getRole() != Role.ADMIN &&
                !currentUser.getCustomerId().equals(customerId))
            throw new AccessDeniedException("Cannot update other customer's profile");

        customerValidator.validateOnUpdate(customerId, request.getPhone());

        Customer customer = findById(customerId);
        String oldValue = toJson(customer);

        customer.updateProfile(
                request.getFullName(),
                request.getPhone(),
                request.getAddress(),
                request.getCity()
        );

        customerRepository.save(customer);

        auditLogPublisher.publish(AuditLogEvent.builder()
                .entityId(customerId)
                .entityType(AuditLogEvent.EntityType.CUSTOMER)
                .action(AuditLogEvent.Action.UPDATE)
                .oldValue(oldValue)
                .newValue(toJson(customer))
                .changedBy(currentUser.getUsername())
                .changedByRole(currentUser.getRole().name())
                .occurredAt(Instant.now())
                .build());

        return customerMapper.toResponse(customer);
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteCustomer(UUID customerId, UserAuth currentUser) {
        Customer customer = findById(customerId);
        String oldValue = toJson(customer);

        customerRepository.delete(customer);

        auditLogPublisher.publish(AuditLogEvent.builder()
                .entityId(customerId)
                .entityType(AuditLogEvent.EntityType.CUSTOMER)
                .action(AuditLogEvent.Action.DELETE)
                .oldValue(oldValue)
                .changedBy(currentUser.getUsername())
                .changedByRole(currentUser.getRole().name())
                .occurredAt(Instant.now())
                .build());

        log.info("Customer deleted: {}", customerId);
    }

    // ── Repository helper ─────────────────────────────────────────────────────

    private Customer findById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize to JSON", e);
            return null;
        }
    }
}