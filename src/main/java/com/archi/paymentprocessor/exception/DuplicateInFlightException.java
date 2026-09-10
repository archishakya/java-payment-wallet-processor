package com.archi.paymentprocessor.exception;

import java.util.UUID;

public class DuplicateInFlightException extends RuntimeException {
    public DuplicateInFlightException(UUID transactionId) {
        super("Transaction " + transactionId + " is already being processed");
    }
}
