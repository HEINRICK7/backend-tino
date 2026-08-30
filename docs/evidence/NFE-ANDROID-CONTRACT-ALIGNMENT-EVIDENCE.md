# TINO — NF-e Android Contract Alignment Evidence

Status: `PASS — B0–B9 TRIAL/LOCAL`
Date: 2026-08-30
Branch: `develop`

## Delivered

- OpenAPI/runtime alignment for snake_case names, typed mobile enums, bearer
  security, required `Idempotency-Key`, examples, and documented errors.
- Rich mobile NF-e response and preview without raw fiscal DTO leakage.
- Tenant-aware bounded Product Search by text or GTIN.
- Explicit confirmation/result read model with GET by `receipt_id`.
- Stable error envelope and fiscal/product/preview/idempotency codes.
- Exactly-once remote confirmation and inventory projection preserved by the
  existing PostgreSQL uniqueness constraints and transactional ports.
- Sync decision recorded: confirmation result/receipt GET is authoritative for
  NF-e Room projection; generic sync pull must not create a second movement.
- No migration was required; the existing V10–V13 schema already supports the
  contract.

## Objective test evidence

| Check | Result |
|---|---|
| `:app:test --tests com.tino.backend.NfeAndroidContractHttpApiTest` | PASS — 3 tests |
| OpenAPI routes/names/enums/idempotency | PASS |
| Trial fixture → preview → product decision → receipt → inventory | PASS |
| Confirmation replay | PASS — same receipt, one movement |
| Decimal quantity | PASS — `5` preserved as decimal semantics |
| Product search and cross-tenant denial | PASS |
| Raw/canonical/parser data absent from mobile response | PASS |
| Fixture | `modules/fiscal/src/test/resources/serpro/consulta-nfe-trial-official-sanitized.json` |
| Secret scan | PASS |
| `git diff --check` | PASS |
| `./gradlew test architecture migrations` | PASS — 196 app tests, architecture and migration tasks |

## Explicitly not proved by this checkpoint

The following remain covered by existing fiscal/unit/integration evidence or
are pending dedicated SERPRO credentials: real SERPRO Trial HTTP smoke, SERPRO
Production/F7, and production raw-fiscal retention policy. No production
credentials were added, and F7 remains `BLOCKED`.

The existing Trial/local suite covers the previously implemented cancellation,
denial, parser retry/outcome-unknown, stale-preview, conversion, and concurrent
confirmation paths. The new HTTP contract test proves the mobile success,
replay, tenant, and no-duplicate-stock path end to end.
