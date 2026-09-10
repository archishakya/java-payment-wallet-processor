# Idempotent Payment/Wallet Event Processor



## Run the tests (this is how the reviewer will evaluate it)

In IntelliJ: open the project (File > Open > select this folder, let it
import as a Maven project), then right-click
`src/test/java/.../TransactionServiceIntegrationTest.java` and choose
**Run**. No external database, no Postman, no config - H2 spins up
in-memory per test run.

From the command line instead:
```
mvn test
```

## Run the app itself
```
mvn spring-boot:run
```
Then:
```
POST http://localhost:8080/api/v1/transactions/process
Content-Type: application/json

{
  "transactionId": "<uuid>",
  "userId": "<uuid>",
  "amount": 250.00,
  "type": "DEBIT"
}
```
Note: there's no endpoint to create a wallet - the test suite seeds wallets
directly via `WalletRepository` in `@BeforeEach`. If you want to hit the
endpoint manually, insert a wallet row via the H2 console first (flip
`spring.h2.console.enabled=true` in `application.properties`), or add a
`POST /api/v1/wallets` endpoint if the assignment scope calls for it.

## Design

- `TransactionRecord` - the idempotency ledger. Unique constraint on
  `transactionId` at the DB level, not app level.
- `IdempotencyGuard` - tries to insert the ledger row in its own
  `REQUIRES_NEW` transaction; a constraint violation means someone else got
  there first.
- `WalletDebitService` - the only place that touches wallet balances, under
  a pessimistic row lock (`SELECT ... FOR UPDATE`).
- `TransactionService` - orchestrates the two above.

See `DECISIONS.md` for the reasoning behind the concurrency approach.
