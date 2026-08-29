# M12 — Messaging Evidence

Status: **PASS — READY FOR INTEGRATION IN `develop`**

## Contract

`docs/milestones/SDD-M12-MESSAGING.md` was completed with owner authorization.
The implementation is intentionally limited to `WHATSAPP` through a
deterministic `sandbox` provider. Real network delivery, credentials,
marketing campaigns, free-form text, and provider callbacks are out of scope.

## Delivered

- `modules/messaging` bounded context with explicit consent, allow-listed
  purpose/template/channel, tenant authorization, and idempotent queueing;
- V9 durable consent, message, delivery-evidence, and outbox tables with
  composite customer FKs, RLS, least-privilege grants, and append-only audit;
- provider port called outside the database transaction, deterministic sandbox
  IDs, durable processing state, retry up to three attempts, and dead-letter;
- no recipient reference or message body exposed in message views or logs;
- HTTP endpoints for consent, queue, process, and tenant-safe status reads.

## Verification

Targeted tests pass locally:

- `M12MessagingPostgresTest`: consent gate, privacy hash, idempotency,
  outbox/evidence durability, append-only audit, authorization, and RLS;
- `M12MessagingHttpApiTest`: consent-required response, queue/replay,
  sandbox processing, and status read;
- `ProcessMessageTest`: retryable provider failure and third-attempt
  dead-letter behavior;
- `MessageDomainTest`: lifecycle transition rules.

Final gates passed locally:

- `./gradlew test architecture migrations --rerun-tasks --no-daemon --console=plain`;
- `./scripts/secret-scan.sh`;
- `git diff --check`.

## Scope audit

M12 does not call a real messaging provider, store credentials, send without
explicit consent, accept free-form content, or mutate financial/reconciliation
records. A production provider requires a separate security and operations
approval.
