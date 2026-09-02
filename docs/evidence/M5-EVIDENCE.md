# M5 Evidence — Bootstrap & Installation Context

Recorded: 2026-08-28 (America/Fortaleza)
Repository: `HEINRICK7/backend-tino`
Milestone: `M5 — BOOTSTRAP & INSTALLATION CONTEXT`
Branch: `feature/m5-bootstrap-context`
Base commit: `6a46de5e65a067739407e3cd7db5ded08bcf14fb`

## Verdict

```text
M5 STATUS: PASS
Bootstrap Context: PASS
BUSINESS_REQUIRED: PASS
LOCAL_BUSINESS_LINK_REQUIRED: PASS
READY: PASS
Identity/Business/Device public contracts: PASS
Authorization ordering: PASS
Cross-business and cross-installation security: PASS
RLS and tenant context reuse: PASS
Read-only behavior: PASS
HTTP security and IDOR protection: PASS
Privacy and token non-disclosure: PASS
No functional migration: PASS
PostgreSQL/Testcontainers: PASS
Spring Modulith: PASS
Build: PASS
Tests: PASS
Secret Scan: PASS
Dependency audit: PASS
Scope Leakage: NONE
M6 AUTHORIZED: NO
```

## Implemented scope

M5 adds the read-only `bootstrap` module and composes the existing public
Identity, Business, and Device contracts. The endpoint is:

```text
POST /api/v1/bootstrap
```

It returns only the approved states and minimal summaries:

- `BUSINESS_REQUIRED` when no operational Business is accessible;
- `LOCAL_BUSINESS_LINK_REQUIRED` when a Business is authorized but no matching
  active installation is available;
- `READY` only after User, membership, Business, and active installation
  authority is established for the same Business.

Bootstrap does not persist state, register devices, create domain events,
create an outbox entry, or implement Sync, Customer, Credit, Ledger, Payment,
Pix, Reconciliation, WhatsApp, or Notification behavior.

## Verification evidence

### Full build and tests

Command:

```bash
./gradlew clean build architecture migrations --rerun-tasks --no-daemon --console=plain
```

Result: **PASS**, `BUILD SUCCESSFUL`.

The `app` suite completed 154 tests with zero failures or errors. The specific
M5 suites completed 61 tests with zero failures or errors:

| Suite | Tests | Result |
|---|---:|---|
| `BootstrapContextTest` | 24 | PASS |
| `M5BootstrapBoundaryScopeTest` | 8 | PASS |
| `M5BootstrapHttpApiTest` | 22 | PASS |
| `M5BootstrapPostgresTest` | 7 | PASS |

Together these cover `TEST-M5-001..055`; the six additional unit assertions
cover privacy, JWT claim non-disclosure, approved summary fields, read-only
behavior, and repeated-resolution stability.

### Architecture, tenancy, and persistence

- `ApplicationModules.of(...).verify()` passed.
- Real PostgreSQL/Testcontainers tests passed for same-tenant visibility,
  cross-tenant isolation, fail-closed tenant context, and pool reset after
  commit/rollback.
- Bootstrap resolves Business before Device and reuses the M4 tenant/RLS
  boundary; it does not create a new RLS policy or alter tenant configuration.
- Schema remains limited to `users`, `businesses`,
  `business_memberships`, and `device_installations`; no V4/V5 migration or
  Bootstrap/session/current-business table exists.

### Security and privacy

- Unauthenticated, invalid, expired, wrong-issuer, wrong-audience, and
  missing-subject requests are rejected by the existing M2 security chain.
- Foreign Business and installation identifiers cannot grant context or cause
  disclosure.
- Responses contain no external subject, JWT claims, token, unnecessary PII,
  Sync data, or business-domain data.
- `git diff --check`: **PASS**.
- `./scripts/secret-scan.sh`: **Secret scan passed.**
- Runtime dependency audit found no JPA/Hibernate ORM, Redis, Kafka,
  RabbitMQ/AMQP, or MongoDB dependency.
- Bootstrap production sources contain no jOOQ or JDBC access.

## Regression correction

The initial full run exposed three stale historical scope assertions, not M5
behavior failures. M3 scope guards now exclude only the explicitly introduced
M5 `BusinessContextReader` contract from M3's historical out-of-scope marker
scan. The M4 jOOQ boundary guard now excludes only the required Modulith
`@NamedInterface` package metadata. No security assertion or production
boundary was weakened; the M5-specific boundary suite remains green.

## Changed scope

The working tree contains the M5 production module, public composition ports,
M5 unit/integration/boundary tests, the required Gradle composition wiring, and
the narrowly scoped M3/M4 guard compatibility corrections. No generated build
artifacts are part of the implementation scope.

## Promotion checkpoint

```text
FEATURE READY FOR DEVELOP
```

The feature branch is ready to be committed and published for promotion to
`develop`. No direct change to `staging` or `main` is authorized by this
evidence.

M6 remains unauthorized.
