# SDD-M12 — Messaging

Status: **AUTHORIZED BY PROJECT OWNER — CONTRACT COMPLETED 2026-08-29**

## GOAL
Deliver approved outbound messages through replaceable adapters.

## IN SCOPE
Transactional `WHATSAPP` messages through a deterministic `sandbox` adapter,
explicit customer consent by purpose, allow-listed templates, idempotent
queueing, durable outbox retry/dead-letter state, tenant-safe status reads,
and delivery evidence.

## OUT OF SCOPE
Real WhatsApp/network calls, provider credentials, marketing campaigns,
free-form message text, sending without consent, customer enrichment, and
direct provider calls inside database transactions.

## DOMAIN CONTRACT
The only channel is `WHATSAPP`; purposes are `TRANSACTIONAL` and `OPERATIONAL`.
Templates are `PAYMENT_UPDATE` and `RECONCILIATION_ALERT`. A message requires
an active consent for its `(customer, channel, purpose)` and an opaque bounded
recipient reference. Lifecycle is `QUEUED -> PROCESSING -> SENT` or
`FAILED`; failed messages retry at most three times, then become `DEAD_LETTER`.
Consent revocation blocks new queue operations and never rewrites history.

## APPLICATION CONTRACT
Consent changes and queueing are local transactions. Queueing writes message,
outbox command, and audit evidence atomically. The worker claims the command,
calls only the provider port outside the transaction, then records a
deterministic sandbox result. Idempotency is scoped by tenant/key and request
fingerprint; retries are bounded and resumable.

## DATABASE CONTRACT
Store only opaque recipient references, template/purpose, hashes, provider IDs,
and timestamps; no message body or phone number. Consent and message identity
are tenant-scoped with composite customer FKs and RLS. Message audit and
delivery evidence are immutable; outbox lifecycle metadata is mutable.

## API CONTRACT
`PUT /api/v1/businesses/{businessId}/customers/{customerId}/messaging/consent`
accepts `{ "channel": "WHATSAPP", "purpose": "TRANSACTIONAL", "granted":
true, "recipient_ref": "opaque-ref" }`.

`POST /api/v1/businesses/{businessId}/customers/{customerId}/messages`
requires `Idempotency-Key` and accepts `{ "channel": "WHATSAPP", "purpose":
"TRANSACTIONAL", "template": "PAYMENT_UPDATE" }`.

`POST /api/v1/businesses/{businessId}/messages/{messageId}/process` processes
one sandbox outbox command; `GET` on the same resource reads status. All
customer/business endpoints require an active authenticated member.

## SECURITY INVARIANTS
Credentials remain runtime-only; technical logs contain no recipient or body.
Templates and purposes are allow-listed, consent is checked transactionally,
and cross-tenant identifiers cannot authorize sends.

## TENANCY INVARIANTS
Templates, recipients and sends are tenant-scoped.

## ACCEPTANCE CRITERIA
Consent grant/revoke, consent-required rejection, idempotent queue/replay and
conflict, exact template/channel validation, retry/dead-letter behavior,
provider boundary, RLS, authorization, privacy minimization, and rollback are
covered by unit and PostgreSQL/HTTP tests.

## REQUIRED TESTS
Consent, replay, retry/dead-letter behavior, callbacks, RLS and privacy logging.

## EVIDENCE
`docs/evidence/M12-EVIDENCE.md` is required before the project goal closes.

## STOP CONDITIONS
Never send without explicit consent and an approved provider contract. The
sandbox adapter is not production delivery; a real provider requires a new
security/operations approval.
