# SDD-M8 — Customer Cloud Model

Status: **AUTHORIZED BY PROJECT OWNER — MINIMAL CONTRACT RECORDED**

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
`POST /api/v1/businesses/{businessId}/customers` requires `Idempotency-Key`;
`GET` collection/item and `PUT` item expose only the approved fields. Create
replay returns the same customer without duplication; key reuse with a
different request is a conflict.

## SECURITY INVARIANTS
Personal data minimized, never cross-tenant or logged.

## TENANCY INVARIANTS
Customer always belongs to an authoritative business.

## ACCEPTANCE CRITERIA
The first executable contract is limited to name, optional nickname, and
optional phone. Customers are Business-owned, active customers are listable,
updates preserve identity/creation time, and archived records are not exposed
by the active collection. No extra personal data or retention behavior is
introduced before a dedicated privacy decision.

## REQUIRED TESTS
Future authorization, RLS, validation, sync and privacy tests.

## EVIDENCE
Future `docs/evidence/M8-EVIDENCE.md`.

## EXECUTION DECISION
The project owner's broad milestone authorization is applied to this minimal
contract only. It is an implementation baseline, not permission to infer CPF,
address, documents, scoring, marketing enrichment, financial behavior, or
provider integrations.

## STOP CONDITIONS
Never add personal fields, retention rules, customer event types, or financial
behavior without a dedicated contract update.
