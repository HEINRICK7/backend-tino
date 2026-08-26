# SDD-M5 — Bootstrap

Status: **DRAFT — NOT AUTHORIZED**

## GOAL
Return the deterministic authenticated startup state needed by Android.

## IN SCOPE
Composition of M2–M4 ports and bootstrap response states.

## OUT OF SCOPE
Android changes, implicit business creation, sync processing.

## DOMAIN CONTRACT
Exactly `BUSINESS_REQUIRED`, `LOCAL_BUSINESS_LINK_REQUIRED`, `READY`.

## APPLICATION CONTRACT
Evaluate user, active memberships, requested local link, and active device without granting authority from client metadata.

## DATABASE CONTRACT
No new tables unless a separately approved revision proves necessity.

## API CONTRACT
`POST /api/v1/bootstrap`.

## SECURITY INVARIANTS
Authenticated user only; do not reveal other tenants.

## TENANCY INVARIANTS
State derives from authorized memberships, never local store ID alone.

## ACCEPTANCE CRITERIA
All three states and negative transitions are deterministic and contract-tested.

## REQUIRED TESTS
State matrix, cross-tenant metadata, revoked device, no-membership, JSON contract.

## EVIDENCE
Future `docs/evidence/M5-EVIDENCE.md`.

## STOP CONDITIONS
No implementation without explicit authorization; stop before M6.
