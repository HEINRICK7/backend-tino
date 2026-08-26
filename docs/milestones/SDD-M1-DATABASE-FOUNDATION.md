# SDD-M1 — Database Foundation

Status: **DRAFT — NOT AUTHORIZED**

## GOAL
Create the first domain schema and prove PostgreSQL conventions/RLS from an empty database.

## IN SCOPE
UUID v7 support; `users`, `businesses`, `business_memberships`, `business_profiles`, `devices`, `sync_events`, `sync_change_log`, `sync_event_rejections`, outbox/audit foundations; FKs/checks/indexes; RLS; app/migration roles.

## OUT OF SCOPE
HTTP endpoints, persisted identity behavior, business/device use cases, bootstrap, event handlers, customers, finance, payments, messaging.

## DOMAIN CONTRACT
Only shared ID/time/money value conventions; no domain workflows.

## APPLICATION CONTRACT
No functional use case.

## DATABASE CONTRACT
Flyway source-of-truth; shared schema; tenant-leading keys/indexes; UUID v7, `TIMESTAMPTZ`, `NUMERIC`, `VARCHAR + CHECK`; non-bypass app role.

## API CONTRACT
None.

## SECURITY INVARIANTS
Least-privilege roles and no superuser test evidence.

## TENANCY INVARIANTS
Prove `INV-TENANT-002/003`; no-context fail-closed and A/B RLS isolation.

## ACCEPTANCE CRITERIA
Zero-to-current migration, schema inspection, real-role grants, forced RLS on every tenant table, and deterministic teardown/rebuild all pass.

## REQUIRED TESTS
`TEST-RLS-001..004`, migration rebuild, constraints/checks, UUID/time/numeric mappings.

## EVIDENCE
Future `docs/evidence/M1-EVIDENCE.md`.

## STOP CONDITIONS
Do not execute without explicit M1 authorization; stop before M2.
