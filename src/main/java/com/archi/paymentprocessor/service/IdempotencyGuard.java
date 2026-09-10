package com.archi.paymentprocessor.service;

import com.archi.paymentprocessor.dto.TransactionRequest;
import com.archi.paymentprocessor.model.TransactionRecord;
import com.archi.paymentprocessor.repository.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Handles the "has this transactionId been seen before" question as its own
 * short, separate transaction (PROPAGATION_REQUIRES_NEW).
 *
 * Why a separate transaction and not just try/catch inside the main one:
 * if the insert fails on the unique constraint, Spring marks the *current*
 * transaction rollback-only. If this insert shared a transaction with the
 * wallet debit, a legitimate duplicate-detection would poison the whole
 * unit of work. Running it in its own transaction means the constraint
 * violation is contained here, and the caller decides what to do next.
 */
@Component
public class IdempotencyGuard {

    private final TransactionRepository transactionRepository;
    private final TransactionTemplate requiresNewTemplate;

    public IdempotencyGuard(TransactionRepository transactionRepository,
                             PlatformTransactionManager transactionManager) {
        this.transactionRepository = transactionRepository;
        this.requiresNewTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTemplate.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Outcome(TransactionRecord record, boolean isNew) {
    }

    public Outcome registerOrFetch(TransactionRequest request) {
        try {
            TransactionRecord saved = requiresNewTemplate.execute(status -> {
                TransactionRecord rec = new TransactionRecord(
                        request.transactionId(), request.userId(), request.amount(), request.type());
                return transactionRepository.saveAndFlush(rec);
            });
            return new Outcome(saved, true);
        } catch (DataIntegrityViolationException duplicate) {
            // Another request already holds this transactionId. Fetch its
            // current state - it may be COMPLETED, FAILED, or still PENDING.
            TransactionRecord existing = transactionRepository.findByTransactionId(request.transactionId())
                    .orElseThrow(() -> duplicate);
            return new Outcome(existing, false);
        }
    }
}
