package com.archi.paymentprocessor;

import com.archi.paymentprocessor.dto.TransactionRequest;
import com.archi.paymentprocessor.dto.TransactionResponse;
import com.archi.paymentprocessor.model.TransactionType;
import com.archi.paymentprocessor.model.Wallet;
import com.archi.paymentprocessor.repository.TransactionRepository;
import com.archi.paymentprocessor.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionServiceIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Processes a single valid debit transaction successfully")
    void processesSingleValidDebitSuccessfully() {
        seedWallet(userId, new BigDecimal("500.00"));
        TransactionRequest request = new TransactionRequest(
                UUID.randomUUID(), userId, new BigDecimal("150.00"), TransactionType.DEBIT);

        ResponseEntity<TransactionResponse> response = post(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("COMPLETED", response.getBody().status());
        assertEquals(0, response.getBody().balanceAfter().compareTo(new BigDecimal("350.00")));

        Wallet wallet = walletRepository.findById(userId).orElseThrow();
        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("350.00")));

        System.out.println("[HappyPathTest] PASSED - single debit of 150.00 processed, balance now "
                + wallet.getBalance());
    }

    @Test
    @DisplayName("Sends 3 identical transactionIds simultaneously - ensures the balance is only deducted once")
    void identicalTransactionIdsAreOnlyProcessedOnce() throws Exception {
        seedWallet(userId, new BigDecimal("500.00"));
        UUID sharedTransactionId = UUID.randomUUID();
        TransactionRequest request = new TransactionRequest(
                sharedTransactionId, userId, new BigDecimal("100.00"), TransactionType.DEBIT);

        List<ResponseEntity<TransactionResponse>> results = fireConcurrently(request, 3);

        long serverErrorCount = results.stream()
                .filter(r -> r.getStatusCode().is5xxServerError())
                .count();
        assertEquals(0, serverErrorCount, "no request should ever surface as a 500");

        List<TransactionResponse> bodies = results.stream()
                .map(ResponseEntity::getBody)
                .filter(java.util.Objects::nonNull)
                .toList();

        long distinctBalances = bodies.stream()
                .map(TransactionResponse::balanceAfter)
                .distinct()
                .count();
        assertEquals(1, distinctBalances,
                "every response that carries a body must agree on the same resulting balance");

        Wallet wallet = walletRepository.findById(userId).orElseThrow();
        assertEquals(0, wallet.getBalance().compareTo(new BigDecimal("400.00")),
                "balance must be debited exactly once despite three identical requests");

        long okCount = results.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
        long conflictCount = results.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();
        System.out.println("[IdempotencyTest] PASSED - ok=" + okCount
                + " conflicts=" + conflictCount + " finalBalance=" + wallet.getBalance());
    }

    @Test
    @DisplayName("Sends 10 concurrent debit requests of 100 for a wallet with a 500 balance - "
            + "ensures the final balance is exactly 0 and 5 requests fail with insufficient funds")
    void tenConcurrentDebitsExhaustBalanceExactly() throws Exception {
        seedWallet(userId, new BigDecimal("500.00"));

        int requestCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ResponseEntity<TransactionResponse>>> futures = new ArrayList<>();

        for (int i = 0; i < requestCount; i++) {

            TransactionRequest request = new TransactionRequest(
                    UUID.randomUUID(), userId, new BigDecimal("100.00"), TransactionType.DEBIT);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return post(request);
            }));
        }

        ready.await();
        go.countDown();

        List<ResponseEntity<TransactionResponse>> results = new ArrayList<>();
        for (Future<ResponseEntity<TransactionResponse>> f : futures) {
            results.add(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();

        long completed = results.stream()
                .filter(r -> r.getBody() != null && "COMPLETED".equals(r.getBody().status()))
                .count();
        long failed = results.stream()
                .filter(r -> r.getBody() != null && "FAILED".equals(r.getBody().status()))
                .count();

        assertEquals(5, completed, "exactly 5 of the 10 debits should succeed");
        assertEquals(5, failed, "exactly 5 of the 10 debits should fail with insufficient funds");

        Wallet wallet = walletRepository.findById(userId).orElseThrow();
        assertEquals(0, wallet.getBalance().compareTo(BigDecimal.ZERO),
                "final balance must be exactly 0, never negative, never left with an untouched 100");

        System.out.println("[RaceConditionTest] PASSED - completed=" + completed
                + " failed=" + failed + " finalBalance=" + wallet.getBalance());
    }

    private List<ResponseEntity<TransactionResponse>> fireConcurrently(TransactionRequest request, int times)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(times);
        CountDownLatch ready = new CountDownLatch(times);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ResponseEntity<TransactionResponse>>> futures = new ArrayList<>();

        for (int i = 0; i < times; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return post(request);
            }));
        }

        ready.await();
        go.countDown();

        List<ResponseEntity<TransactionResponse>> results = new ArrayList<>();
        for (Future<ResponseEntity<TransactionResponse>> f : futures) {
            results.add(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();
        return results;
    }

    private ResponseEntity<TransactionResponse> post(TransactionRequest request) {
        return restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/transactions/process",
                request,
                TransactionResponse.class);
    }

    private void seedWallet(UUID userId, BigDecimal balance) {
        walletRepository.save(new Wallet(userId, balance));
    }
}
