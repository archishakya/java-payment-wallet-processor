package com.archi.paymentprocessor.service;

import com.archi.paymentprocessor.dto.TransactionRequest;
import com.archi.paymentprocessor.dto.TransactionResponse;
import com.archi.paymentprocessor.exception.DuplicateInFlightException;
import com.archi.paymentprocessor.model.TransactionRecord;
import com.archi.paymentprocessor.model.TransactionStatus;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {

    private final IdempotencyGuard idempotencyGuard;
    private final WalletDebitService walletDebitService;

    public TransactionService(IdempotencyGuard idempotencyGuard, WalletDebitService walletDebitService) {
        this.idempotencyGuard = idempotencyGuard;
        this.walletDebitService = walletDebitService;
    }

    public TransactionResponse process(TransactionRequest request) {
        IdempotencyGuard.Outcome outcome = idempotencyGuard.registerOrFetch(request);

        if (outcome.isNew()) {
            return walletDebitService.debit(outcome.record());
        }

        TransactionRecord existing = outcome.record();
        if (existing.getStatus() == TransactionStatus.PENDING) {
            // Someone else is mid-flight on this exact transactionId right now.
            throw new DuplicateInFlightException(request.transactionId());
        }
        // Already finished (COMPLETED or FAILED) - hand back the original outcome
        // instead of touching the wallet again.
        return existing.toResponse();
    }
}
