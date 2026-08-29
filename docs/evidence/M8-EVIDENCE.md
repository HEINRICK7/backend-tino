# M8 Evidence — Customer Cloud Model

Recorded: 2026-08-29 (America/Fortaleza)
Repository: `HEINRICK7/backend-tino`
Milestone: `M8 — CUSTOMER CLOUD MODEL`
Branch: `feature/m8-customer-cloud`
Base: `develop` at `407630d545f7112a7a52f67550922bcc7dd07154`

## Verdict

```text
M8 STATUS: PASS — MINIMAL CONTRACT
Business-owned customer records: PASS
Minimal personal-data surface: PASS
Create/read/update semantics: PASS
Idempotency-Key replay and conflict: PASS
Concurrent create safety: PASS
RLS and cross-business isolation: PASS
Authentication and IDOR denial: PASS
Domain/application jOOQ independence: PASS
Architecture and Modulith regression gates: PASS
Tests: PASS
Secret Scan: PASS
Scope Leakage: NONE
M9 BLOCKED BY FINANCIAL SDD: YES
```

## Implemented scope

M8 adds the `customer` bounded context and migration V5. The model contains
only `name`, optional `nickname`, optional `phone`, lifecycle status, and
Business ownership. The API is:

```text
POST /api/v1/businesses/{businessId}/customers
GET  /api/v1/businesses/{businessId}/customers
GET  /api/v1/businesses/{businessId}/customers/{customerId}
PUT  /api/v1/businesses/{businessId}/customers/{customerId}
```

Create requires `Idempotency-Key`; same-key replay returns the original
customer and a different request fingerprint returns conflict. Concurrent
same-key creates use an atomic database conflict path and leave one customer.
Active collection reads are Business-authorized and tenant-context/RLS
protected. Archived records are not returned by the active collection.

No CPF, address, documents, scoring, marketing enrichment, financial rules,
provider integration, or undefined customer sync event type was added.

## Verification evidence

Commands:

```bash
./gradlew :modules:customer:test --rerun-tasks
./gradlew :app:test --tests com.tino.backend.M8CustomerPostgresTest --rerun-tasks
./gradlew :app:test --tests com.tino.backend.M8CustomerHttpApiTest --rerun-tasks
./gradlew clean build architecture migrations --rerun-tasks --no-daemon --console=plain
./scripts/secret-scan.sh
```

Results: **PASS**. The focused M8 coverage includes 3 unit tests, 4 real
PostgreSQL/Testcontainers tests, and 3 HTTP tests. The complete clean build,
application tests, architecture verification, and migration gate completed
successfully.

The PostgreSQL tests prove idempotent replay, conflict behavior, concurrent
same-key creation, Business authorization, and RLS visibility. HTTP tests
prove authentication enforcement, CRUD behavior, safe foreign-Business
denial, and absence of sensitive error disclosure.

## Contract boundary for later milestones

M9–M12 remain outside this implementation because their SDDs require,
respectively, approved accounting precision/reversal rules, a payment
provider and webhook threat model, reconciliation source/match policy, and
messaging channel/consent/retention rules. No financial or outbound-message
behavior is inferred from this M8 implementation.

## Promotion checkpoint

```text
FEATURE READY FOR DEVELOP
```

The feature is ready to be committed, published, and merged into `develop`.
No direct change to `staging` or `main` is included.
