# SDD-M11 — Reconciliation

Status: **DRAFT — NOT AUTHORIZED; REQUIRES RECONCILIATION SDD**

## GOAL
Compare internal financial truth with provider evidence and resolve discrepancies explainably.

## IN SCOPE
Approved imports/matches/exceptions and compensating workflows.

## OUT OF SCOPE
Silent ledger edits, inferred provider formats, messaging.

## DOMAIN CONTRACT
Match rules, tolerance, states and operator decisions must be specified.

## APPLICATION CONTRACT
Runs are idempotent/resumable and retain evidence.

## DATABASE CONTRACT
Immutable import/run evidence, tenant isolation and traceability to ledger/payment.

## API CONTRACT
Not yet fixed.

## SECURITY INVARIANTS
Financial files/events are sensitive and access-audited.

## TENANCY INVARIANTS
Every reconciliation run is tenant-bound.

## ACCEPTANCE CRITERIA
Blocked until source formats and discrepancy policy are approved.

## REQUIRED TESTS
Duplicate import, deterministic matching, discrepancies, compensation, RLS and rollback.

## EVIDENCE
Future `docs/evidence/M11-EVIDENCE.md`.

## STOP CONDITIONS
Never auto-resolve undefined discrepancies; stop before M12.
