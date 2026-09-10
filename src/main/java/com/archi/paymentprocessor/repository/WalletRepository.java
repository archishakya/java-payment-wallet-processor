package com.archi.paymentprocessor.repository;

import com.archi.paymentprocessor.model.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    /**
     * PESSIMISTIC_WRITE translates to "SELECT ... FOR UPDATE". Any concurrent
     * transaction calling this for the same userId blocks at the database
     * until the holder commits or rolls back - this is what serializes the
     * ten-concurrent-debits race condition test.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);
}
