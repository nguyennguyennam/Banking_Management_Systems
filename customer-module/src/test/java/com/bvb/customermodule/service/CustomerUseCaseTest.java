package com.bvb.customermodule.service;

import com.bvb.customermodule.domain.Customer;
import com.bvb.customermodule.domain.CustomerType;
import com.bvb.customermodule.domain.UserAuth;
import com.bvb.customermodule.repository.CustomerRepository;
import com.bvb.customermodule.repository.CustomerTypeRepository;
import com.bvb.customermodule.repository.UserAuthRepository;
import com.bvb.customermodule.service.DTO.request.CreateCustomerRequest;
import com.bvb.customermodule.service.DTO.request.UpdateCustomerRequest;
import com.bvb.customermodule.service.usecase.CustomerUseCase;
import com.bvb.customermodule.service.util.CustomerMapper;
import com.bvb.customermodule.service.util.CustomerValidator;
import com.bvb.sharedmodule.config.redis.AuditLogPublisher;
import com.bvb.sharedmodule.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerUseCase")
class CustomerUseCaseTest {

    @Mock CustomerRepository     customerRepository;
    @Mock CustomerTypeRepository customerTypeRepository;
    @Mock UserAuthRepository     userAuthRepository;
    @Mock PasswordEncoder        passwordEncoder;
    @Mock CustomerValidator      customerValidator;
    @Mock CustomerMapper         customerMapper;
    @Mock AuditLogPublisher      auditLogPublisher;
    @Mock ObjectMapper           objectMapper;

    @InjectMocks CustomerUseCase useCase;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CustomerType customerType() {
        return CustomerType.builder()
                .id(UUID.randomUUID())
                .typeName(CustomerType.TypeName.INDIVIDUAL)
                .maxTransactionLimit(new BigDecimal("5000.00"))
                .dailyLimit(new BigDecimal("50000.00"))
                .build();
    }

    private Customer customer(UUID id) {
        return Customer.builder()
                .id(id)
                .customerType(customerType())
                .fullName("John Doe")
                .email("john@test.com")
                .phone("0901234567")
                .address("123 Main St")
                .city("HCM")
                .nationalId("123456789012")
                .build();
    }

    private UserAuth userAuth(UUID ownedCustomerId, Role role) {
        return UserAuth.builder()
                .id(UUID.randomUUID())
                .customerId(ownedCustomerId)
                .username("0901234567")
                .passwordHash("hash")
                .role(role)
                .active(true)
                .build();
    }

    private CreateCustomerRequest createRequest() {
        CreateCustomerRequest req = new CreateCustomerRequest();
        ReflectionTestUtils.setField(req, "customerType", CustomerType.TypeName.INDIVIDUAL);
        ReflectionTestUtils.setField(req, "fullName",     "John Doe");
        ReflectionTestUtils.setField(req, "email",        "john@test.com");
        ReflectionTestUtils.setField(req, "phone",        "0901234567");
        ReflectionTestUtils.setField(req, "address",      "123 Main St");
        ReflectionTestUtils.setField(req, "city",         "HCM");
        ReflectionTestUtils.setField(req, "nationalId",   "123456789012");
        ReflectionTestUtils.setField(req, "username",     "0901234567");
        ReflectionTestUtils.setField(req, "password",     "password123");
        return req;
    }

    private UpdateCustomerRequest updateRequest() {
        UpdateCustomerRequest req = new UpdateCustomerRequest();
        ReflectionTestUtils.setField(req, "fullName", "Jane Doe");
        ReflectionTestUtils.setField(req, "phone",    "0909876543");
        ReflectionTestUtils.setField(req, "address",  "456 New St");
        ReflectionTestUtils.setField(req, "city",     "HN");
        return req;
    }

    // ── createCustomer ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createCustomer")
    class CreateCustomer {

        @Test
        @DisplayName("happy path: saves customer and userAuth, publishes audit")
        void happyPath_savesAll() {
            CustomerType type = customerType();
            when(customerTypeRepository.findByTypeName(CustomerType.TypeName.INDIVIDUAL))
                    .thenReturn(Optional.of(type));
            when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(customerMapper.toResponse(any())).thenReturn(mock(
                    com.bvb.customermodule.service.DTO.response.CustomerResponse.class));

            useCase.createCustomer(createRequest());

            verify(customerRepository).save(any(Customer.class));
            verify(userAuthRepository).save(any(UserAuth.class));
            verify(auditLogPublisher).publish(any());
        }

        @Test
        @DisplayName("invalid customer type throws IllegalArgumentException")
        void invalidCustomerType_throws() {
            when(customerTypeRepository.findByTypeName(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.createCustomer(createRequest()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid customer type");

            verifyNoInteractions(customerRepository, userAuthRepository, auditLogPublisher);
        }
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateProfile")
    class UpdateProfile {

        @Test
        @DisplayName("USER updating own profile succeeds")
        void userUpdatingOwnProfile_succeeds() {
            UserAuth currentUser = userAuth(customerId, Role.USER);
            Customer cust        = customer(customerId);

            when(customerRepository.findById(customerId)).thenReturn(Optional.of(cust));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(customerMapper.toResponse(any())).thenReturn(mock(
                    com.bvb.customermodule.service.DTO.response.CustomerResponse.class));

            assertThatCode(() -> useCase.updateProfile(customerId, updateRequest(), currentUser))
                    .doesNotThrowAnyException();

            verify(customerRepository).save(any(Customer.class));
            verify(auditLogPublisher).publish(any());
        }

        @Test
        @DisplayName("USER updating other customer's profile throws AccessDeniedException")
        void userUpdatingOtherProfile_throws() {
            UUID otherCustomerId = UUID.randomUUID();
            UserAuth currentUser = userAuth(otherCustomerId, Role.USER);

            assertThatThrownBy(() -> useCase.updateProfile(customerId, updateRequest(), currentUser))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(customerRepository, auditLogPublisher);
        }

        @Test
        @DisplayName("ADMIN can update any customer profile")
        void adminUpdatingAnyProfile_succeeds() {
            UUID     adminCustomerId = UUID.randomUUID();
            UserAuth adminUser       = userAuth(adminCustomerId, Role.ADMIN);
            Customer cust            = customer(customerId);

            when(customerRepository.findById(customerId)).thenReturn(Optional.of(cust));
            when(customerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(customerMapper.toResponse(any())).thenReturn(mock(
                    com.bvb.customermodule.service.DTO.response.CustomerResponse.class));

            assertThatCode(() -> useCase.updateProfile(customerId, updateRequest(), adminUser))
                    .doesNotThrowAnyException();

            verify(auditLogPublisher).publish(any());
        }

        @Test
        @DisplayName("customer not found throws IllegalArgumentException")
        void customerNotFound_throws() {
            UserAuth adminUser = userAuth(UUID.randomUUID(), Role.ADMIN);
            when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.updateProfile(customerId, updateRequest(), adminUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Customer not found");
        }
    }

    // ── deleteCustomer ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteCustomer")
    class DeleteCustomer {

        @Test
        @DisplayName("deletes customer and publishes audit")
        void deletesAndPublishes() {
            UserAuth adminUser = userAuth(UUID.randomUUID(), Role.ADMIN);
            Customer cust      = customer(customerId);

            when(customerRepository.findById(customerId)).thenReturn(Optional.of(cust));

            useCase.deleteCustomer(customerId, adminUser);

            verify(customerRepository).delete(cust);
            verify(auditLogPublisher).publish(any());
        }

        @Test
        @DisplayName("customer not found throws IllegalArgumentException")
        void customerNotFound_throws() {
            UserAuth adminUser = userAuth(UUID.randomUUID(), Role.ADMIN);
            when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.deleteCustomer(customerId, adminUser))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById: not found throws IllegalArgumentException")
    void getById_notFound_throws() {
        when(customerRepository.findById(customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getById(customerId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer not found");
    }
}
