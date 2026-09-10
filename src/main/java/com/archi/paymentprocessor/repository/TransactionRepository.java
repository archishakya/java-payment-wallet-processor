package com.archi.paymentprocessor.repository;

import com.archi.paymentprocessor.model.TransactionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionRecord, Long> {
    Optional<TransactionRecord> findByTransactionId(UUID transactionId);
}
