package com.bvb.transactionmodule.repository;

import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.service.DTO.request.TransactionSearchRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Specification builder for Transaction search.
 */
public final class TransactionSpec {

    private TransactionSpec() {}

    /** Build Specification from search request — null fields are ignored. */
    public static Specification<Transaction> from(TransactionSearchRequest req) {
        return builder()
                .sourceAccount(req.getSourceAccountId())
                .type(req.getType())
                .status(req.getStatus())
                .amountMin(req.getAmountMin())
                .amountMax(req.getAmountMax())
                .executedFrom(req.getExecutedFrom())
                .executedTo(req.getExecutedTo())
                .createdFrom(req.getCreatedFrom())
                .createdTo(req.getCreatedTo())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    // ── Specification Builder ──────────────────────────────────────────────────

    public static final class Builder {

        private UUID   sourceAccountId;
        private Transaction.Type   type;
        private Transaction.Status status;
        private BigDecimal amountMin;
        private BigDecimal amountMax;
        private Instant executedFrom;
        private Instant executedTo;
        private Instant createdFrom;
        private Instant createdTo;

        private Builder() {}

        public Builder sourceAccount(UUID sourceAccountId) {
            this.sourceAccountId = sourceAccountId;
            return this;
        }

        public Builder type(Transaction.Type type) {
            this.type = type;
            return this;
        }

        public Builder status(Transaction.Status status) {
            this.status = status;
            return this;
        }

        public Builder amountMin(BigDecimal amountMin) {
            this.amountMin = amountMin;
            return this;
        }

        public Builder amountMax(BigDecimal amountMax) {
            this.amountMax = amountMax;
            return this;
        }

        public Builder executedFrom(Instant executedFrom) {
            this.executedFrom = executedFrom;
            return this;
        }

        public Builder executedTo(Instant executedTo) {
            this.executedTo = executedTo;
            return this;
        }

        public Builder createdFrom(Instant createdFrom) {
            this.createdFrom = createdFrom;
            return this;
        }

        public Builder createdTo(Instant createdTo) {
            this.createdTo = createdTo;
            return this;
        }

        /** Compose tất cả predicates bằng AND; null fields bị bỏ qua. */
        public Specification<Transaction> build() {
            return (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();

                if (sourceAccountId != null)
                    predicates.add(cb.equal(root.get("sourceAccountId"), sourceAccountId));

                if (type != null)
                    predicates.add(cb.equal(root.get("transactionType"), type));

                if (status != null)
                    predicates.add(cb.equal(root.get("transactionStatus"), status));

                if (amountMin != null)
                    predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), amountMin));

                if (amountMax != null)
                    predicates.add(cb.lessThanOrEqualTo(root.get("amount"), amountMax));

                if (executedFrom != null)
                    predicates.add(cb.greaterThanOrEqualTo(root.get("executedAt"), executedFrom));

                if (executedTo != null)
                    predicates.add(cb.lessThanOrEqualTo(root.get("executedAt"), executedTo));

                if (createdFrom != null)
                    predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));

                if (createdTo != null)
                    predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));

                return cb.and(predicates.toArray(Predicate[]::new));
            };
        }
    }
}
