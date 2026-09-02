# SDD-M10 — Payments

Status: **AUTHORIZED BY PROJECT OWNER — CONTRACT COMPLETED 2026-08-29**

## GOAL
Integrate approved payment/Pix flows without weakening ledger or tenant guarantees.

## IN SCOPE
BRL/Pix payment intents, a provider port with a deterministic `sandbox` adapter,
idempotent creation, signed webhook ingestion, immutable provider evidence,
durable payment outbox, safe tenant-scoped HTTP reads/commands, and an explicit
payment state machine. The sandbox adapter performs no network call and is the
only provider used by automated acceptance tests.

## OUT OF SCOPE
Production provider credentials/network calls, card data, Pix keys beyond a
non-sensitive external reference, automatic reconciliation (M11), messaging
(M12), installments, fees, chargebacks, partial refunds, and automatic credit
ledger mutation. A captured payment is evidence for M11; it does not create
credit without an explicitly authorized ledger operation.

## DOMAIN CONTRACT
Amounts are positive BRL `NUMERIC(19,2)` values with no implicit rounding.
States and legal transitions are:

`CREATED -> AUTHORIZED | FAILED | CANCELLED`,
`AUTHORIZED -> CAPTURED | FAILED`, and
`CAPTURED -> REFUNDED`.

`REFUNDED`, `FAILED`, and `CANCELLED` are terminal. A payment has one tenant,
customer, provider (`sandbox`), optional provider reference, and an immutable
creation amount/currency. No provider reference can establish tenant authority.

## APPLICATION CONTRACT
Creation and webhook acceptance are local database transactions only. They
never call a provider. Creation writes a `AUTHORIZE_PAYMENT` outbox command;
the worker claims a command, calls the provider outside the transaction, and
records a signed/provider event through an idempotent transition. Replays of
the creation key or provider event are safe. The provider port is the only
place where a future network adapter may be introduced.

## DATABASE CONTRACT
Payment money uses `NUMERIC(19,2)` and checks positive BRL values. Payment
rows are tenant-owned and state changes are constrained by the application and
transition trigger. Provider events are immutable; outbox command identity and
payload are immutable while lifecycle claim/retry metadata is mutable;
provider event IDs and `(business_id,idempotency_key)` are unique. The outbox
has claim/retry metadata and is tenant-scoped with RLS.

## API CONTRACT
`POST /api/v1/businesses/{businessId}/customers/{customerId}/payments`
creates a payment intent and requires `Idempotency-Key`; it returns `201` or
the replayed `200` result. The request is `{ "amount": "10.00", "method":
"PIX", "external_reference": "..." }`.

`GET /api/v1/businesses/{businessId}/payments/{paymentId}` reads the current
state. `POST /api/v1/businesses/{businessId}/payments/{paymentId}/process`
is the deterministic worker command for the sandbox adapter.

`POST /api/v1/businesses/{businessId}/payment-webhooks/{provider}` accepts a raw JSON body with
`payment_id`, `provider_payment_id`, and `status`; it requires
`X-Provider-Event-Id` and `X-Provider-Signature`. The sandbox signature is
`HMAC-SHA256(body, TINO_SANDBOX_WEBHOOK_SECRET)` and secrets are runtime-only.

## SECURITY INVARIANTS
Verify provider name, event ID, constant-time signature, status, and payment
identity before changing state. Webhook processing is tenant-bound by the
payment itself and never trusts a provider business ID. Store only a payload
SHA-256, not raw webhook/Pix personal data. No secret is committed.

## TENANCY INVARIANTS
Provider references cannot establish tenant authority.

## ACCEPTANCE CRITERIA
Create/replay/conflict, exact money precision, legal/illegal transitions,
signed/forged/replayed webhooks, provider timeout/retry, outbox durability,
rollback, RLS, authorization, and cross-tenant references are covered by
unit and PostgreSQL/HTTP tests. The payment module remains independent of
provider SDKs and of M11/M12.

## REQUIRED TESTS
Replay, webhook forgery, timeout/retry, rollback, precision and RLS.

## EVIDENCE
`docs/evidence/M10-EVIDENCE.md` is required before M11 starts.

## STOP CONDITIONS
No real provider call or secret without a separately approved production
adapter/security contract. M11 starts only after this contract, implementation,
gates, and evidence are integrated in `develop`.
