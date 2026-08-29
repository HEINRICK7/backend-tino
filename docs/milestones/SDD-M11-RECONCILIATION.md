# SDD-M11 — Reconciliation

Status: **AUTHORIZED BY PROJECT OWNER — CONTRACT COMPLETED 2026-08-29**

## GOAL
Compare internal financial truth with provider evidence and resolve discrepancies explainably.

## IN SCOPE
Normalized provider settlement imports for `sandbox`, deterministic matching
against M10 payments, immutable run/item evidence, idempotent event handling,
tenant-safe queries, and explicit discrepancy classification.

## OUT OF SCOPE
Provider-specific file parsing, silent ledger edits, automatic compensation,
manual resolution commands, tolerance/rounding, production provider calls,
and messaging. A discrepancy remains open until a separately authorized
operator workflow exists.

## DOMAIN CONTRACT
The import is already normalized and contains provider event ID, provider
payment ID, amount, currency, and settlement status. Amounts are exact BRL
`NUMERIC(19,2)` values; no tolerance or rounding exists. Each item is classified
as `MATCHED`, `MISSING_PAYMENT`, `AMOUNT_MISMATCH`, `STATUS_MISMATCH`, or
`DUPLICATE_EVENT`. Runs are `PROCESSING`, `COMPLETED`, or `FAILED`.

An item is `MATCHED` only when provider/payment IDs identify the same tenant
payment, amount equals exactly, currency is BRL, and provider status is
`CAPTURED`. No classification changes financial truth.

## APPLICATION CONTRACT
Every run has an idempotency key and request fingerprint. Provider event IDs
are unique within a provider and tenant; re-importing an event returns the
existing item. The complete normalized input is represented by hashes and
bounded fields, not raw sensitive files. Matching reads M10 payment truth and
never calls a provider or mutates M9.

## DATABASE CONTRACT
Run identity and items are immutable; a run's lifecycle counters/state are
updated only once from `PROCESSING` to a terminal state. Both are
tenant-owned and RLS-protected. Run identity is unique by tenant/idempotency
key; item identity is unique by tenant/run/provider event ID. Items retain
payment linkage when present, normalized amount/status, payload hash,
classification, and timestamps. Foreign keys cannot cross tenants.

## API CONTRACT
`POST /api/v1/businesses/{businessId}/reconciliation/runs` requires
`Idempotency-Key` and accepts `{ "provider": "sandbox", "entries": [{
"provider_event_id": "...", "provider_payment_id": "...", "amount":
"10.00", "currency": "BRL", "status": "CAPTURED" }] }`.

`GET /api/v1/businesses/{businessId}/reconciliation/runs/{runId}` returns the
run and item classifications. All endpoints require an authenticated active
tenant member.

## SECURITY INVARIANTS
Only normalized bounded data and SHA-256 input fingerprints are retained.
Provider IDs cannot grant tenant access. Reads and imports are authorized by
the business membership and access failures disclose no cross-tenant evidence.

## TENANCY INVARIANTS
Every reconciliation run is tenant-bound.

## ACCEPTANCE CRITERIA
Duplicate import, replay/conflict, exact matching, each discrepancy class,
rollback, immutable evidence, RLS, authorization, and cross-tenant references
are covered by unit and PostgreSQL/HTTP tests. M11 has no automatic financial
correction.

## REQUIRED TESTS
Duplicate import, deterministic matching, discrepancies, compensation, RLS and rollback.

## EVIDENCE
`docs/evidence/M11-EVIDENCE.md` is required before M12 starts.

## STOP CONDITIONS
Never auto-resolve or mutate undefined discrepancies. M12 starts only after
this contract, implementation, gates, and evidence are integrated in
`develop`.
