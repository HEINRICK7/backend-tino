# SDD-M6 — Sync Push

Status: **DRAFT — NOT AUTHORIZED**

## GOAL
Accept Android event batches with tenant-safe, transactional, idempotent processing.

## IN SCOPE
`POST /v1/sync/events`, exact envelope/result compatibility, application service, handler registry, event claim, rejection records, change log/outbox writes.

## OUT OF SCOPE
Pull, new Android events/domain behavior without separate specification, later business domains.

## DOMAIN CONTRACT
Handlers own event semantics and declared schema versions; controllers own none.

## APPLICATION CONTRACT
Validate actor/membership/device; process each accepted event transactionally; replay returns already processed.

## DATABASE CONTRACT
Unique `(business_id,event_id)` and atomic event/effects/change/outbox writes.

## API CONTRACT
Exact discovered push endpoint, fields, acknowledged/already-processed/rejected result.

## SECURITY INVARIANTS
Envelope identifiers never override authenticated tenant; revoked/foreign devices reject.

## TENANCY INVARIANTS
Every lookup/write uses authorized `business_id` plus RLS.

## ACCEPTANCE CRITERIA
Prove `INV-SYNC-001/002/004` and `INV-OUTBOX-001`; unknown type/version rejects explicitly.

## REQUIRED TESTS
`TEST-SYNC-001..004`, rollback, concurrency/replay, Android JSON contract.

## EVIDENCE
Future `docs/evidence/M6-EVIDENCE.md`.

## STOP CONDITIONS
No execution without authorization; stop before M7.
