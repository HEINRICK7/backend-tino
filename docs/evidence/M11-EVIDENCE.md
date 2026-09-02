# M11 — Reconciliation Evidence

Status: **PASS — READY FOR INTEGRATION IN `develop`**

## Contract

`docs/milestones/SDD-M11-RECONCILIATION.md` was completed after the project
owner authorized M11. The source is normalized `sandbox` settlement evidence;
there is no provider file parser, tolerance, automatic correction, or silent
ledger mutation.

## Delivered

- `modules/reconciliation` bounded context consuming the M10 payment read port;
- V8 tenant-isolated run/item evidence with exact BRL matching;
- idempotent run creation with fingerprint conflict detection;
- classifications `MATCHED`, `MISSING_PAYMENT`, `AMOUNT_MISMATCH`,
  `STATUS_MISMATCH`, and `DUPLICATE_EVENT`;
- run counters and terminal state transition guarded in PostgreSQL;
- immutable item evidence, composite tenant foreign keys, RLS, and safe HTTP
  import/read endpoints;
- no automatic compensation or messaging side effect.

## Verification

Targeted PostgreSQL and HTTP tests pass locally, covering exact matching,
missing payment, amount discrepancy, replay/conflict, immutable evidence,
authentication, and normalized import behavior.

Final gates passed locally:

- `./gradlew test architecture migrations --rerun-tasks --no-daemon --console=plain`;
- `./scripts/secret-scan.sh`;
- `git diff --check`.

## Scope audit

M11 does not parse provider-specific formats, modify M9 credit, resolve
discrepancies automatically, call providers, or send messages. M12 is the
next milestone and must consume explicit consent and durable outbox rules.
