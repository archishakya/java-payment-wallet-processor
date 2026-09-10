package com.archi.paymentprocessor.service;

import com.archi.paymentprocessor.dto.TransactionResponse;
import com.archi.paymentprocessor.exception.WalletNotFoundException;
import com.archi.paymentprocessor.model.TransactionRecord;
import com.archi.paymentprocessor.model.Wallet;
import com.archi.paymentprocessor.repository.TransactionRepository;
import com.archi.paymentprocessor.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Deliberately a separate Spring bean (not a private method on
 * TransactionService). @Transactional only works through the Spring proxy;
 * calling an annotated method on "this" from within the same class bypasses
 * the proxy and silently runs with no transaction at all. Putting this in
 * its own bean means the call always crosses a proxy boundary.
 */
@Service
public class WalletDebitService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public WalletDebitService(WalletRepository walletRepository, TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResponse debit(TransactionRecord pending) {
        // Pessimistic write lock: this blocks other threads debiting the
        // same wallet until this transaction commits or rolls back.
        Wallet wallet = walletRepository.findByUserIdForUpdate(pending.getUserId())
                .orElseThrow(() -> new WalletNotFoundException(pending.getUserId()));

        if (wallet.getBalance().compareTo(pending.getAmount()) < 0) {
            pending.markFailed("Insufficient funds", wallet.getBalance());
            transactionRepository.save(pending);
            return pending.toResponse();
        }

        BigDecimal newBalance = wallet.getBalance().subtract(pending.getAmount());
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        pending.markCompleted(newBalance);
        transactionRepository.save(pending);
        return pending.toResponse();
    }
}
