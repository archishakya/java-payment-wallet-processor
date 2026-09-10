package com.archi.paymentprocessor.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        String status,
        BigDecimal balanceAfter,
        String message
) {
}
