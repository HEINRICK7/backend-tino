# TINO Android → Backend Discovery

Status: **OBSERVED CLIENT CONSTRAINT**

The Android application is local-first. It already has a local `storeId`, installation `deviceId`, outbox/events, WorkManager delivery, cursor, and `eventId` deduplication. Real authentication does not yet exist.

## Current contract

- `POST /v1/sync/events`
- `GET /v1/sync/changes?cursor=...&limit=100`
- Envelope: `event_id`, `store_id`, `device_id`, `aggregate_id`, `event_type`, `schema_version`, `occurred_at`, `payload`.
- Push result: `acknowledged_event_ids`, `already_processed_event_ids`, and `rejected` entries with `eventId`, `code`, `retryable`, `message`.

## Compatibility constraints

- Do not change endpoints or fields silently.
- `store_id` remains metadata only.
- Same-device changes may be returned; Android deduplicates by `event_id`.
- Delivery is at-least-once; pull ordering uses a server sequence, never time or UUID.
- Android changes require separate authorization.
