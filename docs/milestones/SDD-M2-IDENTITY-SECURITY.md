# SDD-M2 — Identity & Security

Status: **DRAFT — NOT AUTHORIZED**

## GOAL
Authenticate people via Keycloak/OIDC and map validated subjects to internal users.

## IN SCOPE
Spring Security resource server, JWT validation/mapping, user upsert/read ports/adapters, security error contract, Keycloak test configuration.

## OUT OF SCOPE
Business membership authorization, business/device/bootstrap/sync endpoints.

## DOMAIN CONTRACT
User identity is stable external `sub`; profile claims are mutable attributes.

## APPLICATION CONTRACT
Resolve or provision the internal user for a validated principal.

## DATABASE CONTRACT
Use only M1 `users`; no tenant table behavior.

## API CONTRACT
Authentication behavior for technical probes and future protected surfaces; no new domain endpoint.

## SECURITY INVARIANTS
Prove `INV-AUTH-001`; default deny and invalid/expired/wrong-issuer rejection.

## TENANCY INVARIANTS
Authentication never implies a business.

## ACCEPTANCE CRITERIA
Valid subject maps deterministically; invalid tokens fail; secrets/tokens are not logged; application starts against Keycloak-compatible configuration.

## REQUIRED TESTS
JWT claim/issuer/expiry/signature tests, unique-subject concurrency test, architecture dependency test.

## EVIDENCE
Future `docs/evidence/M2-EVIDENCE.md`.

## STOP CONDITIONS
No implementation without explicit authorization; stop before M3.
