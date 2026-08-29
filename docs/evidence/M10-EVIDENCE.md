# M10 — Payments / Pix Evidence

Status: **PASS — READY FOR INTEGRATION IN `develop`**

## Contract

`docs/milestones/SDD-M10-PAYMENTS.md` was completed after the project owner
authorized M10. The implementation is deliberately limited to BRL/Pix payment
intents, deterministic `sandbox` provider behavior, signed webhook evidence,
and a durable outbox. No provider credential or network call is part of this
milestone.

## Delivered

- `modules/payment` bounded context with domain, application ports/use cases,
  HTTP adapters, jOOQ persistence, and sandbox provider adapter;
- exact BRL amount validation and an explicit forward-only payment state
  machine;
- idempotent payment creation with tenant-scoped customer reference;
- durable `AUTHORIZE_PAYMENT` outbox with claim, retry, and completion state;
- provider processing outside the database transaction;
- signed webhook endpoint with constant-time HMAC verification;
- immutable provider event evidence, provider-event replay handling, RLS,
  composite tenant foreign keys, and safe HTTP errors;
- historical migration/fixture checks updated from V6 to V7 without weakening
  earlier milestone behavior.

## Verification

Targeted M10 PostgreSQL and HTTP tests pass locally, covering creation,
replay/conflict, outbox processing, provider events, legal/illegal transitions,
signature forgery, signed webhook replay, authorization, RLS, and cross-tenant
references.

Final gates passed locally:

- `./gradlew test architecture migrations --rerun-tasks --no-daemon --console=plain`;
- `./scripts/secret-scan.sh`;
- `git diff --check`.

## Scope audit

M10 does not implement real provider network calls, production secrets, cards,
fees, installments, chargebacks, automatic credit-ledger mutation,
reconciliation, or messaging. M11 is the next milestone and consumes payment
events as evidence.
