# SDD-M9 — Credit / Ledger

Status: **DRAFT — NOT AUTHORIZED; REQUIRES FINANCIAL SDD**

## GOAL
Implement explainable customer credit on an append-only ledger.

## IN SCOPE
Only financial contracts approved in a dedicated SDD: entries, compensation, balances, concurrency and audit separation.

## OUT OF SCOPE
Payments/Pix, reconciliation and mutation/deletion of confirmed entries.

## DOMAIN CONTRACT
Prove `INV-FIN-001`; confirmed entries are immutable and correction compensates.

## APPLICATION CONTRACT
Commands are idempotent, atomic and produce explainable results.

## DATABASE CONTRACT
`NUMERIC`, FKs, append-only protections, RLS, constraints and justified locking/versioning.

## API CONTRACT
Not yet fixed.

## SECURITY INVARIANTS
Least privilege and separate audit trail.

## TENANCY INVARIANTS
All accounts/entries tenant-owned and isolated.

## ACCEPTANCE CRITERIA
Blocked until accounting signs, precision, reversal and concurrency rules are approved.

## REQUIRED TESTS
Immutability, compensation, precision, concurrency, rollback, RLS and idempotency.

## EVIDENCE
Future `docs/evidence/M9-EVIDENCE.md`.

## STOP CONDITIONS
Never invent accounting behavior; stop before M10.
