# SDD-M10 — Payments

Status: **DRAFT — NOT AUTHORIZED; REQUIRES PROVIDER/SECURITY SDD**

## GOAL
Integrate approved payment/Pix flows without weakening ledger or tenant guarantees.

## IN SCOPE
Provider adapter, idempotent commands/webhooks, outbox, status model and Pix data explicitly approved.

## OUT OF SCOPE
Provider choice, keys or financial semantics inferred during implementation; reconciliation (M11).

## DOMAIN CONTRACT
Payment state machine must be specified before execution.

## APPLICATION CONTRACT
No external API inside DB transactions; durable effects via outbox/worker.

## DATABASE CONTRACT
Money precision, immutable evidence, unique provider/idempotency keys, RLS.

## API CONTRACT
Not yet fixed.

## SECURITY INVARIANTS
Verify webhook authenticity; secrets external; minimize Pix/personal data.

## TENANCY INVARIANTS
Provider references cannot establish tenant authority.

## ACCEPTANCE CRITERIA
Blocked until provider, state, retry and threat contracts are approved.

## REQUIRED TESTS
Replay, webhook forgery, timeout/retry, rollback, precision and RLS.

## EVIDENCE
Future `docs/evidence/M10-EVIDENCE.md`.

## STOP CONDITIONS
No provider calls without approved adapter contract; stop before M11.
