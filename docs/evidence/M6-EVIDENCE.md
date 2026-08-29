# M6 Evidence — Sync Push

Recorded: 2026-08-29 (America/Fortaleza)
Repository: `HEINRICK7/backend-tino`
Milestone: `M6 — SYNC PUSH`
Branch: `feature/m6-sync-push`
Base: `develop` at `39719aae497a38c8f999a82b442bf3acd2d3725f`

## Verdict

```text
M6 STATUS: PASS
Android push envelope/result: PASS
Authentication and business context: PASS
Device authorization and store metadata boundary: PASS
Handler registry and unknown-event rejection: PASS
Idempotent claim (business_id,event_id): PASS
Transactional claim/change/outbox writes: PASS
Replay and concurrent winner: PASS
Tenant RLS and fail-closed behavior: PASS
Architecture and Modulith regression gates: PASS
Tests: PASS
Secret Scan: PASS
Scope Leakage: NONE
M7 AUTHORIZED: YES
```

## Implemented scope

M6 adds `POST /v1/sync/events` and the Sync Push module. The application
service resolves the authenticated user and authorized Business, validates the
active installation, routes the event through a registered `(event_type,
schema_version)` handler, claims `(business_id,event_id)`, and commits the
claim, immutable change, and transactional outbox record in one tenant
transaction. Replays return `already_processed_event_ids` without applying
effects. Unknown handlers, unauthorized devices, and handler-level domain
rejections are reported explicitly and do not create accepted effects.

`store_id` is persisted as event metadata only. It never selects or grants a
Business. If the authenticated user has multiple accessible Businesses, the
request must provide the explicit `business_id` context; an omitted context is
rejected without tenant writes.

## Verification evidence

Commands:

```bash
./gradlew :modules:sync:test --rerun-tasks
./gradlew :app:test --tests com.tino.backend.M6SyncPushPostgresTest --rerun-tasks
./gradlew :app:test --tests com.tino.backend.M6SyncPushHttpApiTest --rerun-tasks
./gradlew :app:test --tests com.tino.backend.M1ArchitectureTest --rerun-tasks
./gradlew test
./scripts/secret-scan.sh
```

Results: **PASS**. The focused M6 suites cover 6 unit tests, 6 real
PostgreSQL/Testcontainers tests, and 2 HTTP contract tests. The complete
repository test task completed successfully with no failures.

The PostgreSQL suite proves acceptance, replay, concurrent idempotency,
rollback of the complete write set, explicit unknown-event rejection, and
unauthorized-device rejection. The HTTP suite proves unauthenticated access
is rejected and the Android envelope/result fields serialize compatibly.

The global architecture gate was extended only to classify the new Sync jOOQ
adapter as persistence; domain and application contracts remain jOOQ-free.
Historical migration/cleanup assertions were updated to include V4 while
preserving the existing tenant fixtures and boundaries.

## Promotion checkpoint

```text
FEATURE READY FOR DEVELOP
```

The feature is ready to be committed, published, and merged into `develop`.
No direct change to `staging` or `main` is included.
