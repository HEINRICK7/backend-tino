# SDD-M9 — Credit / Ledger

Status: **AUTHORIZED BY PROJECT OWNER — CONTRACT COMPLETED 2026-08-29**

## GOAL

Provide an explainable, tenant-isolated customer credit balance backed by an
append-only ledger. Every balance-affecting operation must be atomic,
idempotent, and traceable to its actor and reason.

## DECISIONS RECORDED FOR THIS IMPLEMENTATION

- Currency is `BRL` for M9. Amounts use `NUMERIC(19,2)`, positive values only,
  scale at most two, with no implicit rounding or floating-point conversion.
- An account is created lazily for one `(business, customer, currency)` pair.
- M9 has two confirmed entry directions: `CREDIT` increases balance and
  `DEBIT` decreases it. A debit is rejected when it would make the balance
  negative.
- Entries are confirmed at insertion. There is no pending state in M9.
- A confirmed entry cannot be updated or deleted. Correction is a single full
  compensating entry with the opposite direction and an explicit link to the
  original. A compensation cannot itself be compensated. If reversing a credit
  would make the balance negative, the correction is rejected atomically and
  must be handled by a separately authorized business operation.
- Every write command requires an `Idempotency-Key`, scoped by business and
  operation. Reusing a key with a different request fingerprint is a conflict.
- The account row is locked for the complete append operation. The ledger
  remains the historical source of explanation; the account balance is a
  materialized projection maintained in the same transaction.
- `reason` is a required bounded domain code supplied by the caller. M9 does
  not infer payment, reconciliation, or messaging semantics from it.
- Audit records are separate from ledger entries, append-only, tenant-owned,
  and contain actor, operation, entry linkage, idempotency key, and a payload
  hash; sensitive request payloads are not stored.

## IN SCOPE

Credit accounts, confirmed credit/debit entries, full compensations, balance
queries, idempotent commands, tenant isolation, database append-only guards,
and a separate audit trail.

## OUT OF SCOPE

Payments/Pix, provider integrations, reconciliation, messaging, credit scoring,
credit limits, interest, installments, currency conversion, pending entries,
partial compensation, and mutation/deletion of confirmed entries.

## DOMAIN CONTRACT

1. `CreditAmount` is finite, strictly positive, has scale `0..2`, and fits
   `NUMERIC(19,2)`.
2. A `CREDIT` adds the amount; a `DEBIT` subtracts it.
3. An account balance is always non-negative.
4. Entries are immutable facts. A correction appends the opposite amount and
   references the original entry; the original is never changed.
5. Only an original entry may be compensated, and it may be compensated once.
6. The entry business and account business must be identical, and the account
   customer must belong to that business.

## APPLICATION CONTRACT

- `POST /api/v1/businesses/{businessId}/customers/{customerId}/credit/entries`
  appends a confirmed entry and returns the resulting balance.
- `POST /api/v1/businesses/{businessId}/customers/{customerId}/credit/entries/{entryId}/compensation`
  appends a full compensating entry and returns the resulting balance.
- `GET /api/v1/businesses/{businessId}/customers/{customerId}/credit` returns
  the account balance and currency, creating no account or entry.
- Write endpoints require authentication, active membership, active business,
  a nonblank `Idempotency-Key`, and a request fingerprint. Replays return the
  original result; a fingerprint mismatch returns `409`.
- A failed command leaves no entry, balance change, idempotency claim, or audit
  record.

## DATABASE CONTRACT

- Flyway owns all schema changes.
- Accounts and entries use UUID v7 application-generated identifiers.
- Amounts are `NUMERIC(19,2)` and constrained to be positive.
- Composite tenant/customer and tenant/account foreign keys prevent cross-tenant
  references.
- An insert trigger locks the account and updates the materialized balance;
  balance has a non-negative check. Update/delete triggers reject ledger and
  audit mutation.
- RLS is enabled and forced on accounts, entries, idempotency, and audit tables;
  every query also includes an explicit business predicate.
- The application role receives only the minimum table privileges needed by the
  use cases.

## SECURITY INVARIANTS

- Tenant authority is derived from JWT → User → Membership → Business; request
  `businessId` is only a resource selector.
- No provider or payment reference can establish tenant authority.
- Error responses expose stable codes and correlation IDs, never SQL details,
  stack traces, account existence across tenants, or request payloads.

## ACCEPTANCE CRITERIA

- Domain tests prove amount precision, direction, non-negative balance,
  compensation, and immutable-entry rules.
- PostgreSQL tests prove append-only triggers, atomic balance updates,
  concurrent debit serialization, rollback, idempotency, composite FKs, RLS,
  and audit separation.
- HTTP tests prove authentication, membership/tenant isolation, validation,
  replay/conflict behavior, and stable safe errors.
- Architecture tests prove domain/application packages do not import Spring,
  jOOQ, JDBC, or provider types.

## REQUIRED TESTS

Immutability, compensation, precision, concurrency, rollback, RLS, composite
tenant references, idempotency, audit separation, authentication, and HTTP
error privacy.

## EVIDENCE

`docs/evidence/M9-EVIDENCE.md` is produced only after the implementation gates,
secret scan, CI, and integration into `develop` pass.

## STOP CONDITIONS

Do not add payment/provider/reconciliation/messaging behavior to M9. Do not
round money implicitly. Do not update or delete confirmed entries. Do not
proceed to M10 until this contract and its evidence are complete.
