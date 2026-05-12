package com.bvb.accountmodule.service.usecase;

import com.bvb.sharedmodule.event.TransactionResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferResultPublisher {

    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publish(UUID transactionId, UUID accountId, boolean success,
                        BigDecimal fee, String failureReason, String performedBy) {
        Objects.requireNonNull(accountId, "accountId must not be null");
        eventPublisher.publishEvent(
                new TransactionResultEvent(transactionId, accountId, success, fee, failureReason, performedBy));
        log.debug("[RESULT] txId={} success={} accountId={}", transactionId, success, accountId);
    }
}
