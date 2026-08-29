# M9 — Credit / Ledger Evidence

Status: **PASS — INTEGRATED IN `develop`**

Implementation commit: `b0a9c0a`  
Pull request: [#15](https://github.com/HEINRICK7/backend-tino/pull/15)  
Integration merge: `5121c9ebc3561a06504fb0bc9a099f179ce128a5`

## Contract

The M9 contract was completed in
`docs/milestones/SDD-M9-CREDIT-LEDGER.md` after the project owner authorized
the necessary contract decisions. It fixes BRL, `NUMERIC(19,2)`, positive
amounts without implicit rounding, confirmed immutable entries, non-negative
balances, full compensation, idempotency, account-row locking, RLS, composite
tenant references, and separate append-only audit records.

## Delivered

- `modules/credit` bounded context with domain, application ports/use cases,
  HTTP adapters, and jOOQ persistence adapter;
- lazy tenant/customer credit accounts with materialized balance and version;
- atomic `CREDIT`/`DEBIT` append operations;
- full compensating entries linked to their immutable originals;
- idempotency keys scoped by business and operation with fingerprint conflict;
- database checks, composite foreign keys, RLS/forced RLS, append-only triggers,
  balance trigger, and one-compensation unique index;
- safe HTTP errors without database or cross-tenant disclosure;
- earlier milestone fixtures updated to include V6 without weakening their
  historical behavior.

## Verification

Local gates passed on the M9 branch:

- `./gradlew test` — PASS;
- `./gradlew clean build architecture migrations --rerun-tasks --no-daemon --console=plain` — PASS;
- `./scripts/secret-scan.sh` — PASS.

The M9 unit, PostgreSQL, HTTP, concurrency, RLS, composite-FK, idempotency,
rollback, audit, and append-only tests passed. PR #15 remote `gates` and
GitGuardian checks passed before merge into `develop`.

## Scope audit

M9 does not implement payments/Pix, provider calls, reconciliation, messaging,
credit scoring, credit limits, interest, installments, currency conversion,
pending entries, partial compensation, or mutation/deletion of confirmed
entries. M10 is the next milestone and must use M9's ledger contract as its
financial boundary.
