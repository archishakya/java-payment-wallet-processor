package com.archi.paymentprocessor.model;

import com.archi.paymentprocessor.dto.TransactionResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "transaction_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_transaction_id", columnNames = "transaction_id"))
public class TransactionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "balance_after", precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TransactionRecord() {
        // JPA
    }

    public TransactionRecord(UUID transactionId, UUID userId, BigDecimal amount, TransactionType type) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.type = type;
        this.status = TransactionStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void markCompleted(BigDecimal balanceAfter) {
        this.status = TransactionStatus.COMPLETED;
        this.balanceAfter = balanceAfter;
        this.message = "Processed";
    }

    public void markFailed(String reason, BigDecimal balanceAfter) {
        this.status = TransactionStatus.FAILED;
        this.balanceAfter = balanceAfter;
        this.message = reason;
    }

    public TransactionResponse toResponse() {
        return new TransactionResponse(transactionId, status.name(), balanceAfter, message);
    }

    public Long getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }
}
