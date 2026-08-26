# SDD-M12 — Messaging

Status: **DRAFT — NOT AUTHORIZED; REQUIRES CHANNEL/CONSENT SDD**

## GOAL
Deliver approved outbound messages through replaceable adapters.

## IN SCOPE
Templates, consent, provider adapter, outbox worker, idempotent delivery and status callbacks when approved.

## OUT OF SCOPE
WhatsApp/provider selection inferred during coding, marketing without consent, direct calls inside DB transactions.

## DOMAIN CONTRACT
Message purpose, recipient, consent, template and delivery lifecycle must be specified.

## APPLICATION CONTRACT
Queue durably after commit; retry with bounded policy and idempotency.

## DATABASE CONTRACT
Minimal personal data, retention, RLS and delivery evidence.

## API CONTRACT
Not yet fixed.

## SECURITY INVARIANTS
Credentials external; content/phone data excluded from technical logs.

## TENANCY INVARIANTS
Templates, recipients and sends are tenant-scoped.

## ACCEPTANCE CRITERIA
Blocked until channel, consent, retention and provider contracts are approved.

## REQUIRED TESTS
Consent, replay, retry/dead-letter behavior, callbacks, RLS and privacy logging.

## EVIDENCE
Future `docs/evidence/M12-EVIDENCE.md`.

## STOP CONDITIONS
Never send without explicit authorization/consent semantics; milestone ends here.
