# M7 Evidence — Sync Pull

Recorded: 2026-08-29 (America/Fortaleza)
Repository: `HEINRICK7/backend-tino`
Milestone: `M7 — SYNC PULL`
Branch: `feature/m7-sync-pull`
Base: `develop` at `93a30ebc375ccded27053e66f5f2455898395d87`

## Verdict

```text
M7 STATUS: PASS
GET /v1/sync/changes: PASS
Server sequence cursor: PASS
Ascending bounded pagination: PASS
Equal-timestamp stability: PASS
Empty page cursor preservation: PASS
Business authorization and A/B isolation: PASS
Android-compatible change JSON: PASS
Cursor/limit validation: PASS
Architecture and regression gates: PASS
Tests: PASS
Secret Scan: PASS
Scope Leakage: NONE
M8 AUTHORIZED: YES
```

## Implemented scope

M7 adds `GET /v1/sync/changes?cursor=...&limit=100`. The server uses the
monotonic `sync_changes.sequence_id` as an opaque cursor, selects only rows
strictly after the cursor, orders ascending, applies a maximum page size of
100, and returns the last delivered sequence as `next_cursor`. Empty pages
preserve the received cursor. Timestamp and UUID cursors are not accepted.

Business authorization is resolved before the page query and the query runs
inside the existing transaction-local tenant/RLS boundary. An optional
`business_id` query parameter makes the Business context explicit for users
with multiple accessible Businesses; omission in that case fails closed.
The change envelope exposes only the approved event fields and preserves the
JSON payload as an object.

## Verification evidence

Commands:

```bash
./gradlew :modules:sync:test --rerun-tasks
./gradlew :app:test --tests com.tino.backend.M7SyncPullPostgresTest --rerun-tasks
./gradlew :app:test --tests com.tino.backend.M7SyncPullHttpApiTest --rerun-tasks
./gradlew test
./scripts/secret-scan.sh
```

Results: **PASS**. The focused M7 suites cover 4 unit tests, 2 real
PostgreSQL/Testcontainers tests, and 2 HTTP contract tests. The complete
repository test task completed successfully with no failures.

The PostgreSQL tests prove sequence pagination across equal timestamps,
bounded pages, empty-page behavior, and tenant-isolated pages for Businesses
A and B. The HTTP tests prove authentication enforcement, ordered change
JSON, object payload preservation, and `next_cursor` serialization.

## Promotion checkpoint

```text
FEATURE READY FOR DEVELOP
```

The feature is ready to be committed, published, and merged into `develop`.
No direct change to `staging` or `main` is included.
