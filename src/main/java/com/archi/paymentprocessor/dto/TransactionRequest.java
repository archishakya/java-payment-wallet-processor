package com.archi.paymentprocessor.dto;

import com.archi.paymentprocessor.model.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionRequest(
        UUID transactionId,
        UUID userId,
        BigDecimal amount,
        TransactionType type
) {
}
