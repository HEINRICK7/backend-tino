# SDD-M7 — Sync Pull

Status: **AUTHORIZED BY PROJECT OWNER**

## GOAL
Provide deterministic, tenant-safe incremental change pagination.

## IN SCOPE
`GET /v1/sync/changes`, sequential cursor, bounded limit, ordered changes and next cursor.

## OUT OF SCOPE
Timestamp/UUID cursors, suppression of same-device changes, websockets/push notifications.

## DOMAIN CONTRACT
Cursor is an opaque server sequence position, not business time.

## APPLICATION CONTRACT
Return only authorized changes with `sequence_id > cursor`, ascending and limited.

## DATABASE CONTRACT
Tenant-leading `(business_id,sequence_id)` access path and immutable change records.

## API CONTRACT
Existing Android query parameters and compatible change envelope.

## SECURITY INVARIANTS
Cursor cannot reveal or cross tenant boundaries.

## TENANCY INVARIANTS
Prove A/B isolation for every page.

## ACCEPTANCE CRITERIA
Prove `INV-SYNC-003`, stable pagination under equal timestamps, bounds and empty pages.

## REQUIRED TESTS
`TEST-SYNC-005..007`, cursor validation, concurrent append pagination, JSON contract.

## EVIDENCE
Future `docs/evidence/M7-EVIDENCE.md`.

## STOP CONDITIONS
No execution without authorization; stop before M8.
