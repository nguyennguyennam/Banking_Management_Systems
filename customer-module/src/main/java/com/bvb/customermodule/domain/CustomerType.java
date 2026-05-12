package com.bvb.customermodule.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_type")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CustomerType {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @ColumnTransformer(read = "type_name::text", write = "?::customer_type_name_")
    @Column(name = "type_name", nullable = false, unique = true,
            columnDefinition = "CUSTOMER_TYPE_NAME_")
    private TypeName typeName;

    @Column(name = "max_transaction_limit", nullable = false,
            precision = 18, scale = 2)
    private BigDecimal maxTransactionLimit;

    @Column(name = "daily_limit", nullable = false,
            precision = 18, scale = 2)
    private BigDecimal dailyLimit;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum TypeName {
        INDIVIDUAL, BUSINESS
    }
}