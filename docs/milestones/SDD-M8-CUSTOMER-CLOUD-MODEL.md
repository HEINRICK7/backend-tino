# SDD-M8 — Customer Cloud Model

Status: **DRAFT — NOT AUTHORIZED; REQUIRES DOMAIN DISCOVERY**

## GOAL
Define the minimal tenant-owned customer model compatible with local-first sync.

## IN SCOPE
Only name/nickname and phone unless discovery proves more; lifecycle, ports, persistence, APIs/events explicitly approved here.

## OUT OF SCOPE
CPF, address, documents, scoring, credit/ledger, marketing enrichment.

## DOMAIN CONTRACT
Must be completed by customer discovery before authorization.

## APPLICATION CONTRACT
Tenant-scoped create/read/update semantics with idempotency and sync compatibility.

## DATABASE CONTRACT
RLS/FKs/conventions from baseline; personal-data minimization.

## API CONTRACT
Not yet fixed.

## SECURITY INVARIANTS
Personal data minimized, never cross-tenant or logged.

## TENANCY INVARIANTS
Customer always belongs to an authoritative business.

## ACCEPTANCE CRITERIA
Blocked until domain/API contracts and retention/privacy requirements are approved.

## REQUIRED TESTS
Future authorization, RLS, validation, sync and privacy tests.

## EVIDENCE
Future `docs/evidence/M8-EVIDENCE.md`.

## STOP CONDITIONS
Missing discovery is an explicit stop; never infer extra personal data.
