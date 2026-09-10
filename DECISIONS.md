# Decision Log

## 1. How did you handle the concurrency race condition?

Two separate mechanisms, for two separate problems:

- **Duplicate detection (idempotency):** a unique DB constraint on
  `transaction_records.transaction_id`. When three identical requests race,
  all three try to insert a row with the same `transactionId`; the database
  guarantees only one insert wins, and it does this atomically regardless of
  timing - no application-level "check then insert" logic, which would have
  its own race condition between the check and the insert.
- **Balance safety (locking):** `SELECT ... FOR UPDATE` (JPA
  `PESSIMISTIC_WRITE`) on the wallet row inside the debit transaction. Any
  other thread trying to debit the same wallet blocks at the database level
  until the current transaction commits, so reads of the balance are never
  stale when a write happens.
- The idempotency check runs in its own `REQUIRES_NEW` transaction so a
  constraint violation there doesn't roll back or interfere with the wallet
  debit transaction.

_(Add anything you'd change with more time - e.g. how this would need to
change for a multi-instance deployment where H2 isn't in play, or how you'd
handle the wallet-not-found edge case differently.)_

## 2. Where did your AI assistant give you an incorrect or sub-optimal suggestion?

_This one has to come from you - it's the part of the assignment that's
actually checking whether you understood the code well enough to catch
something. Some real candidates to watch for as you review this project in
IntelliJ, in case any of them turn out to be true for you:_

- _Did an assistant ever suggest `synchronized` or an in-memory
  `ConcurrentHashMap` for the idempotency check? That would only work within
  a single JVM instance and silently breaks the moment this runs behind a
  load balancer with more than one instance._
- _Did an assistant suggest checking for an existing transaction with a
  plain `findByTransactionId` before inserting, rather than relying on the
  DB constraint? That "check-then-insert" pattern has a race condition
  between the check and the insert._
- _Did you have to correct a suggestion about calling an `@Transactional`
  method from within the same class (the self-invocation problem this
  project avoids by splitting `WalletDebitService` into its own bean)?_

Write down what actually happened for you - what you asked, what you got,
what was wrong about it, and how you caught it.
