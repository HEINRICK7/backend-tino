# System Specification — Synchronization

Status: **APPROVED CONTRACT; PUSH STARTS M6, PULL M7**

Preserve the Android endpoints and fields in the discovery report. Processing flows `SyncController → SyncApplicationService → EventHandlerRegistry`; controllers contain no event-type logic. Unknown types/versions reject explicitly.

One accepted-event transaction validates membership/device, claims `(business_id,event_id)`, invokes one handler, writes changes/outbox, and commits. Replay returns already-processed without effects. Pull selects `sequence_id > cursor`, tenant-scoped, ascending, bounded, and returns the last delivered sequence. Same-device changes may return. Semantics are at-least-once plus idempotency, never distributed exactly-once.

`TEST-SYNC-001..007` cover replay, rollback, unauthorized device, registry routing, deterministic pagination, and exact Android JSON compatibility.
