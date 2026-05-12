package com.bvb.transactionmodule.controller;

import com.bvb.transactionmodule.config.exception.RollbackExhaustedException;
import com.bvb.transactionmodule.config.exception.TransactionNotFoundException;
import com.bvb.transactionmodule.domain.Transaction;
import com.bvb.transactionmodule.domain.TransactionRollback;
import com.bvb.transactionmodule.service.DTO.response.RollbackResponse;
import com.bvb.transactionmodule.service.DTO.response.TransactionResponse;
import com.bvb.transactionmodule.service.usecase.TransactionRollbackService;
import com.bvb.transactionmodule.service.usecase.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer slice test for TransactionController.
 * Spring Security is disabled so tests can focus on request/response mapping.
 * The @AuthenticationPrincipal is provided via @WithMockUser.
 */
@WebMvcTest(
        value = TransactionController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@DisplayName("TransactionController")
class TransactionControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TransactionService         transactionService;
    @MockBean TransactionRollbackService rollbackService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/transactions
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/transactions")
    class CreateTransaction {

        @Test
        @WithMockUser(username = "testuser", roles = "USER")
        @DisplayName("valid DEPOSIT request → 201 with transaction response")
        void validDepositRequest_returns201() throws Exception {
            UUID txId = UUID.randomUUID();
            TransactionResponse response = buildTransactionResponse(txId, Transaction.Status.COMPLETED,
                    Transaction.Type.DEPOSIT);

            when(transactionService.createTransaction(any(), eq("testuser"))).thenReturn(response);

            mockMvc.perform(post("/api/transactions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(depositRequestJson()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(txId.toString()))
                    .andExpect(jsonPath("$.transactionStatus").value("COMPLETED"))
                    .andExpect(jsonPath("$.transactionType").value("DEPOSIT"));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("missing amount → 400 validation error")
        void missingAmount_returns400() throws Exception {
            mockMvc.perform(post("/api/transactions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "sourceAccountId",  UUID.randomUUID().toString(),
                                    "transactionType",  "DEPOSIT",
                                    "idempotencyKey",   UUID.randomUUID().toString()
                                    // amount missing
                            ))))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(transactionService);
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("amount below minimum (0.00) → 400 validation error")
        void amountBelowMinimum_returns400() throws Exception {
            mockMvc.perform(post("/api/transactions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "sourceAccountId",  UUID.randomUUID().toString(),
                                    "transactionType",  "DEPOSIT",
                                    "amount",           "0.00",
                                    "idempotencyKey",   UUID.randomUUID().toString()
                            ))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("service throws IllegalArgumentException → 400")
        void serviceThrowsIllegalArgument_returns400() throws Exception {
            when(transactionService.createTransaction(any(), any()))
                    .thenThrow(new IllegalArgumentException("TRANSFER transaction requires targetAccountId"));

            mockMvc.perform(post("/api/transactions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(depositRequestJson()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("TRANSFER")));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("service throws IllegalStateException → 422")
        void serviceThrowsIllegalState_returns422() throws Exception {
            when(transactionService.createTransaction(any(), any()))
                    .thenThrow(new IllegalStateException("Account is not active"));

            mockMvc.perform(post("/api/transactions")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(depositRequestJson()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value(containsString("not active")));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/transactions/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/transactions/{id}")
    class GetById {

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("existing transaction → 200 with body")
        void existingTransaction_returns200() throws Exception {
            UUID txId    = UUID.randomUUID();
            TransactionResponse response = buildTransactionResponse(txId,
                    Transaction.Status.COMPLETED, Transaction.Type.DEPOSIT);
            when(transactionService.getById(txId)).thenReturn(response);

            mockMvc.perform(get("/api/transactions/{id}", txId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(txId.toString()))
                    .andExpect(jsonPath("$.transactionStatus").value("COMPLETED"));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("non-existent transaction → 404")
        void nonExistent_returns404() throws Exception {
            UUID unknownId = UUID.randomUUID();
            when(transactionService.getById(unknownId))
                    .thenThrow(new TransactionNotFoundException(unknownId));

            mockMvc.perform(get("/api/transactions/{id}", unknownId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value(containsString("Transaction not found")));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/transactions/account/{accountId}
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/transactions/account/{accountId}")
    class GetByAccount {

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("returns 200 with paginated response")
        void returnsPagedResults() throws Exception {
            UUID accountId = UUID.randomUUID();
            UUID txId      = UUID.randomUUID();
            TransactionResponse response = buildTransactionResponse(txId,
                    Transaction.Status.COMPLETED, Transaction.Type.DEPOSIT);

            when(transactionService.getByAccount(eq(accountId), any()))
                    .thenReturn(new PageImpl<>(List.of(response)));

            mockMvc.perform(get("/api/transactions/account/{accountId}", accountId)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(txId.toString()))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @WithMockUser(roles = "USER")
        @DisplayName("empty result set → 200 with empty content")
        void emptyResult_returns200WithEmptyContent() throws Exception {
            UUID accountId = UUID.randomUUID();
            when(transactionService.getByAccount(eq(accountId), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/transactions/account/{accountId}", accountId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/transactions/{id}/rollback
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/transactions/{id}/rollback")
    class Rollback {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        @DisplayName("valid rollback request → 200 with rollback response")
        void validRollbackRequest_returns200() throws Exception {
            UUID txId    = UUID.randomUUID();
            RollbackResponse response = buildRollbackResponse(txId);
            when(rollbackService.rollback(eq(txId), eq("Fraud detected"), eq("admin")))
                    .thenReturn(response);

            mockMvc.perform(post("/api/transactions/{id}/rollback", txId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("reason", "Fraud detected"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.originalTransactionId").value(txId.toString()))
                    .andExpect(jsonPath("$.rollbackStatus").value("COMPLETED"))
                    .andExpect(jsonPath("$.message").value("Transaction rolled back successfully."));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("blank reason → 400 validation error")
        void blankReason_returns400() throws Exception {
            UUID txId = UUID.randomUUID();
            mockMvc.perform(post("/api/transactions/{id}/rollback", txId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("reason", ""))))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(rollbackService);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("missing reason field → 400 validation error")
        void missingReason_returns400() throws Exception {
            UUID txId = UUID.randomUUID();
            mockMvc.perform(post("/api/transactions/{id}/rollback", txId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("transaction not found → 404")
        void transactionNotFound_returns404() throws Exception {
            UUID txId = UUID.randomUUID();
            when(rollbackService.rollback(eq(txId), any(), any()))
                    .thenThrow(new TransactionNotFoundException(txId));

            mockMvc.perform(post("/api/transactions/{id}/rollback", txId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("reason", "fraud"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("transaction not COMPLETED → 422")
        void transactionNotCompleted_returns422() throws Exception {
            UUID txId = UUID.randomUUID();
            when(rollbackService.rollback(eq(txId), any(), any()))
                    .thenThrow(new IllegalStateException("Chỉ có thể rollback giao dịch COMPLETED."));

            mockMvc.perform(post("/api/transactions/{id}/rollback", txId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("reason", "error"))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail").value(containsString("COMPLETED")));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("rollback exhausted after 3 attempts → 422 with attempt info")
        void rollbackExhausted_returns422WithAttemptInfo() throws Exception {
            UUID txId = UUID.randomUUID();
            when(rollbackService.rollback(eq(txId), any(), any()))
                    .thenThrow(new RollbackExhaustedException(txId, 3));

            mockMvc.perform(post("/api/transactions/{id}/rollback", txId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("reason", "error"))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.transactionId").value(txId.toString()))
                    .andExpect(jsonPath("$.attempts").value(3))
                    .andExpect(jsonPath("$.support").exists());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String depositRequestJson() throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "sourceAccountId", UUID.randomUUID().toString(),
                "transactionType", "DEPOSIT",
                "amount",          "500.00",
                "idempotencyKey",  UUID.randomUUID().toString()
        ));
    }

    private TransactionResponse buildTransactionResponse(UUID id,
                                                          Transaction.Status status,
                                                          Transaction.Type type) {
        return TransactionResponse.builder()
                .id(id)
                .sourceAccountId(UUID.randomUUID())
                .transactionType(type)
                .transactionStatus(status)
                .amount(new BigDecimal("500.00"))
                .transactionFee(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .build();
    }

    private RollbackResponse buildRollbackResponse(UUID txId) {
        TransactionRollback record = TransactionRollback.completed(txId, "Fraud detected", "admin");
        return RollbackResponse.from(record, "Transaction rolled back successfully.");
    }
}
