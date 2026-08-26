# SDD-M4 — Device Registration

Status: **DRAFT — NOT AUTHORIZED**

## GOAL
Link and revoke Android installations under an authorized business.

## IN SCOPE
Device domain/ports/adapters, active/revoked states, compatibility `local_store_id`, idempotent link API.

## OUT OF SCOPE
Bootstrap orchestration and sync processing.

## DOMAIN CONTRACT
Device ID identifies an installation; store ID is metadata and cannot grant authority.

## APPLICATION CONTRACT
Link only for active membership; replay is idempotent; revoked device remains rejected until explicitly reauthorized by a defined operation.

## DATABASE CONTRACT
Use M1 device constraints and tenant policies.

## API CONTRACT
`POST /api/v1/devices/link` with `Idempotency-Key`.

## SECURITY INVARIANTS
Prove `INV-DEVICE-001`; spoofed business/store/device combinations fail.

## TENANCY INVARIANTS
Device access is always business-scoped and membership-backed.

## ACCEPTANCE CRITERIA
Authorized link/replay pass; unauthorized/revoked/cross-tenant attempts fail with no partial state.

## REQUIRED TESTS
Membership, duplicate, spoofing, revocation, rollback, RLS.

## EVIDENCE
Future `docs/evidence/M4-EVIDENCE.md`.

## STOP CONDITIONS
No implementation without explicit authorization; stop before M5.
