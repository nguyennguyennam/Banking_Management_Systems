package com.bvb.accountmodule.service.usecase;

import com.bvb.sharedmodule.event.DepositRequestedEvent;
import com.bvb.sharedmodule.event.TransferRequestedEvent;
import com.bvb.sharedmodule.event.WithdrawalRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferEventHandler {

    private final AccountTransactionService accountService;
    private final TransferResultPublisher   resultPublisher;

    @Async("transferExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDeposit(DepositRequestedEvent event) {
        log.info("[HANDLER] DEPOSIT txId={} accountId={} amount={}",
                event.transactionId(), event.accountId(), event.amount());
        try {
            BigDecimal fee = accountService.deposit(event.accountId(), event.amount(), event.transactionId());
            resultPublisher.publish(event.transactionId(), event.accountId(), true, fee, null, event.requestedBy());
        } catch (Exception e) {
            log.error("[HANDLER] DEPOSIT FAILED txId={} error={}", event.transactionId(), e.getMessage());
            resultPublisher.publish(event.transactionId(), event.accountId(), false, BigDecimal.ZERO, e.getMessage(), event.requestedBy());
        }
    }

    @Async("transferExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWithdrawal(WithdrawalRequestedEvent event) {
        log.info("[HANDLER] WITHDRAWAL txId={} accountId={} amount={}",
                event.transactionId(), event.accountId(), event.amount());
        try {
            BigDecimal fee = accountService.withdraw(event.accountId(), event.amount(), event.transactionId());
            resultPublisher.publish(event.transactionId(), event.accountId(), true, fee, null, event.requestedBy());
        } catch (Exception e) {
            log.error("[HANDLER] WITHDRAWAL FAILED txId={} error={}", event.transactionId(), e.getMessage());
            resultPublisher.publish(event.transactionId(), event.accountId(), false, BigDecimal.ZERO, e.getMessage(), event.requestedBy());
        }
    }

    @Async("transferExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransfer(TransferRequestedEvent event) {
        log.info("[HANDLER] TRANSFER txId={} source={} target={} amount={}",
                event.transactionId(), event.sourceAccountId(), event.targetAccountId(), event.amount());
        try {
            BigDecimal fee = accountService.transfer(
                    event.sourceAccountId(), event.targetAccountId(), event.amount(), event.transactionId());
            resultPublisher.publish(event.transactionId(), event.sourceAccountId(), true, fee, null, event.requestedBy());
        } catch (Exception e) {
            log.error("[HANDLER] TRANSFER FAILED txId={} error={}", event.transactionId(), e.getMessage());
            resultPublisher.publish(event.transactionId(), event.sourceAccountId(), false, BigDecimal.ZERO, e.getMessage(), event.requestedBy());
        }
    }
}
