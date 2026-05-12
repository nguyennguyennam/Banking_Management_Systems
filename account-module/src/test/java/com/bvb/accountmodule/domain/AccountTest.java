package com.bvb.accountmodule.domain;

import com.bvb.sharedmodule.exception.InsufficientBalanceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Account")
class AccountTest {

    private Account build(BigDecimal balance, BigDecimal limit) {
        return Account.builder()
                .id(UUID.randomUUID())
                .customerId(UUID.randomUUID())
                .accountNumber("1234567890")
                .accountType(Account.AccountType.PAYMENT)
                .balance(balance)
                .transactionLimit(limit)
                .status(Account.Status.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("credit")
    class Credit {

        @Test
        @DisplayName("adds amount to balance")
        void addsAmountToBalance() {
            Account a = build(new BigDecimal("100.00"), new BigDecimal("5000.00"));
            a.credit(new BigDecimal("50.00"));
            assertThat(a.getBalance()).isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("adding zero does not change balance")
        void addingZeroKeepsBalance() {
            Account a = build(new BigDecimal("100.00"), new BigDecimal("5000.00"));
            a.credit(BigDecimal.ZERO);
            assertThat(a.getBalance()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("credit accumulates across multiple calls")
        void multipleCredits_accumulate() {
            Account a = build(BigDecimal.ZERO, new BigDecimal("5000.00"));
            a.credit(new BigDecimal("100.00"));
            a.credit(new BigDecimal("250.00"));
            assertThat(a.getBalance()).isEqualByComparingTo("350.00");
        }
    }

    @Nested
    @DisplayName("debit")
    class Debit {

        @Test
        @DisplayName("subtracts amount from balance")
        void subtractsFromBalance() {
            Account a = build(new BigDecimal("200.00"), new BigDecimal("5000.00"));
            a.debit(new BigDecimal("80.00"));
            assertThat(a.getBalance()).isEqualByComparingTo("120.00");
        }

        @Test
        @DisplayName("exact balance reduces to zero")
        void exactBalance_reducesToZero() {
            Account a = build(new BigDecimal("100.00"), new BigDecimal("5000.00"));
            a.debit(new BigDecimal("100.00"));
            assertThat(a.getBalance()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("amount exceeds balance throws InsufficientBalanceException")
        void exceedsBalance_throws() {
            Account a = build(new BigDecimal("50.00"), new BigDecimal("5000.00"));
            assertThatThrownBy(() -> a.debit(new BigDecimal("100.00")))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("Insufficient balance");
        }

        @Test
        @DisplayName("amount exceeds transaction limit throws InsufficientBalanceException")
        void exceedsTransactionLimit_throws() {
            Account a = build(new BigDecimal("10000.00"), new BigDecimal("500.00"));
            assertThatThrownBy(() -> a.debit(new BigDecimal("600.00")))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("exceeds the allowed limit");
        }

        @Test
        @DisplayName("limit check precedes balance check when amount exceeds both")
        void exceedsBothLimitAndBalance_throwsLimitMessage() {
            Account a = build(new BigDecimal("100.00"), new BigDecimal("50.00"));
            assertThatThrownBy(() -> a.debit(new BigDecimal("200.00")))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("exceeds the allowed limit");
        }
    }

    @Nested
    @DisplayName("status transitions")
    class StatusTransitions {

        @Test
        @DisplayName("new account is ACTIVE")
        void newAccount_isActive() {
            Account a = build(BigDecimal.ZERO, new BigDecimal("5000.00"));
            assertThat(a.getStatus()).isEqualTo(Account.Status.ACTIVE);
            assertThat(a.isActive()).isTrue();
        }

        @Test
        @DisplayName("lock sets status to LOCKED and isActive false")
        void lock_setsLocked() {
            Account a = build(BigDecimal.ZERO, new BigDecimal("5000.00"));
            a.lock();
            assertThat(a.getStatus()).isEqualTo(Account.Status.LOCKED);
            assertThat(a.isActive()).isFalse();
        }

        @Test
        @DisplayName("unlock after lock sets status to ACTIVE and isActive true")
        void unlock_setsActive() {
            Account a = build(BigDecimal.ZERO, new BigDecimal("5000.00"));
            a.lock();
            a.unlock();
            assertThat(a.getStatus()).isEqualTo(Account.Status.ACTIVE);
            assertThat(a.isActive()).isTrue();
        }

        @Test
        @DisplayName("close sets status to CLOSED and isActive false")
        void close_setsClosed() {
            Account a = build(BigDecimal.ZERO, new BigDecimal("5000.00"));
            a.close();
            assertThat(a.getStatus()).isEqualTo(Account.Status.CLOSED);
            assertThat(a.isActive()).isFalse();
        }
    }
}
