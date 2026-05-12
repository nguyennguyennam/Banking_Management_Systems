package com.bvb.customermodule.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Customer {

        @Id
        @Column(nullable = false, updatable = false)
        private UUID id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "customer_type_id", nullable = false)
        private CustomerType customerType;

        @Column(name = "full_name", nullable = false, length = 255)
        private String fullName;

        @Column(nullable = false, unique = true, length = 255)
        private String email;

        @Column(nullable = false, unique = true, length = 20)
        private String phone;

        @Column(nullable = false, length = 255)
        private String address;

        @Column(nullable = false, length = 100)
        private String city;

        @Column(name = "national_id", nullable = false, unique = true, length = 20)
        private String nationalId;

        @CreatedDate
        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @LastModifiedDate
        @Column(name = "updated_at", nullable = false)
        private Instant updatedAt;

        // ── Business methods ───────────────────────────────────────────────────────

        public void updateProfile(String email, String phone,
                                  String address, String city) {

                if (!email.isEmpty() && !email.equals(this.email)) {
                        this.email = email;
                }
                if (!phone.isEmpty() && !phone.equals(this.phone)) {
                        this.phone = phone;
                }

                if (!address.isEmpty() && !address.equals(this.address)) {
                        this.address = address;
                }

                if (!city.isEmpty() && !city.equals(this.city)) {
                        this.city = city;
                }
        }

        public void changeType(CustomerType newType) {
                this.customerType = newType;
        }
}