# SDD-M3 — Business / Membership

Status: **DRAFT — NOT AUTHORIZED**

## GOAL
Create/list businesses and profiles while proving membership-based tenant authority.

## IN SCOPE
Business verticals `RETAIL`, `BAKERY`, `RESTAURANT`, `STORE`, `OTHER`; owner membership on create; list/profile APIs and ports/adapters.

## OUT OF SCOPE
Devices, bootstrap orchestration, sync and later domains.

## DOMAIN CONTRACT
Business is the tenant; membership has explicit role/status; no invented vertical.

## APPLICATION CONTRACT
Create business+owner atomically; list only active memberships; fetch authorized profile.

## DATABASE CONTRACT
Use M1 business/profile/membership tables with explicit tenant predicates and RLS.

## API CONTRACT
`POST /api/v1/businesses`, `GET /api/v1/businesses`, `GET /api/v1/businesses/{id}/profile`; critical create requires `Idempotency-Key`.

## SECURITY INVARIANTS
JWT user plus active membership; indistinguishable unauthorized/not-found where appropriate.

## TENANCY INVARIANTS
Prove `INV-TENANT-001/002`; `storeId` is irrelevant.

## ACCEPTANCE CRITERIA
Atomic owner creation, idempotent create, A/B isolation, exact vertical validation, and no jOOQ leak.

## REQUIRED TESTS
Authorization, RLS, duplicate key, rollback, enum checks, Modulith tests.

## EVIDENCE
Future `docs/evidence/M3-EVIDENCE.md`.

## STOP CONDITIONS
No implementation without explicit authorization; stop before M4.
