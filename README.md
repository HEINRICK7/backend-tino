# TINO Backend

Production-grade modular monolith for the TINO Android synchronization backend.

## Prerequisites

- Java 21
- Docker (for integration tests and local PostgreSQL)

## Verification

```bash
./gradlew clean build architecture migrations
```

The verification suite uses Testcontainers and real PostgreSQL for Flyway,
jOOQ, identity, membership, device installation, RLS, and Bootstrap Context
flows. The current schema contains `users`, `businesses`,
`business_memberships`, and `device_installations`; Bootstrap is read-only and
does not add a functional migration.

## Local runtime

```bash
./scripts/create-local-env.sh
set -a
. ./.env
set +a
docker compose up --build
```

Compose starts the Spring Boot application, PostgreSQL, and Keycloak in separate containers. Local Compose defaults `TINO_FISCAL_MODE=fixture`, which enables the official sanitized Trial fixture without SERPRO credentials; use `TINO_FISCAL_MODE=serpro` only when Trial credentials are supplied through the environment. For a shorter feedback loop, the application can still run directly through Gradle after starting only the infrastructure services with `docker compose up -d postgres keycloak`.
If the default ports are occupied, use `TINO_APP_PORT=58080 TINO_POSTGRES_PORT=55433 TINO_KEYCLOAK_PORT=58082 docker compose up --build`.

Run the authenticated NF-e Trial smoke test against a running Compose stack with:

```bash
TINO_APP_PORT=58080 TINO_KEYCLOAK_PORT=58082 ./scripts/trial-smoke-test.sh
```

The script creates or resets only its local Keycloak test user, retrieves the
sanitized official Trial fixture, verifies that preview does not change stock,
confirms the receipt transactionally, checks confirmation idempotency, and
reprocesses the fiscal document. It never prints access tokens or passwords.

`scripts/create-local-env.sh` generates local credentials at runtime into the ignored, mode-`600` `.env` file and refuses to overwrite an existing file. Compose reads it automatically; exporting it as shown also supplies Spring Boot and jOOQ. No password has a committed default. Production credentials and private keys must come from the platform secret store and must never be committed.

## Local secret gate

CI scans tracked files for hardcoded credential material. Enable the same gate before every local commit and push with:

```bash
git config core.hooksPath .githooks
```

The hooks invoke the scan before commit and again before push; CI independently scans all tracked file categories. No external scanner or API credential is required for this local baseline, while GitGuardian remains the remote security authority.

Set `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` to the Keycloak realm URL. Health and OpenAPI are public foundation surfaces; every other route is authenticated by default.

Swagger UI is available at `http://localhost:8080/swagger-ui.html`; liveness/readiness endpoints are below `/actuator/health`.

External business catalogs are backend-only. Register a provider with
`POST /api/v1/businesses/{businessId}/external-connections`, inspect
`GET /api/v1/businesses/{businessId}/external-connections` or
`GET /api/v1/businesses/{businessId}/data-source`, and start a sync with
`POST /api/v1/businesses/{businessId}/external-connections/{connectionId}/sync`.
The first provider is `DOCES_SONHOS`. Its current public catalog contract is
`GET https://api.doces-sonhos.otimizanegocio.com/public/products` and its
OpenAPI is available at
`https://api.doces-sonhos.otimizanegocio.com/api-json` (Swagger UI at
`/api`). The base URL and path can be overridden with
`TINO_EXTERNAL_DOCES_SONHOS_BASE_URL` and
`TINO_EXTERNAL_DOCES_SONHOS_PRODUCTS_PATH`. The endpoint is public, so
`TINO_EXTERNAL_DOCES_SONHOS_API_TOKEN` is optional and is sent only when
configured for a gateway. No credential is accepted or returned by the TINO
API, Android, logs, fixtures, or persistence.

The current functional entry point is `POST /api/v1/bootstrap`, which returns
the initial authenticated user/business/installation state without performing
sync or other domain operations.
