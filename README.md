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

Compose starts the Spring Boot application, PostgreSQL, and Keycloak in separate containers. For a shorter feedback loop, the application can still run directly through Gradle after starting only the infrastructure services with `docker compose up -d postgres keycloak`.
If the default ports are occupied, use `TINO_APP_PORT=58080 TINO_POSTGRES_PORT=55432 TINO_KEYCLOAK_PORT=58081 docker compose up --build`.

`scripts/create-local-env.sh` generates local credentials at runtime into the ignored, mode-`600` `.env` file and refuses to overwrite an existing file. Compose reads it automatically; exporting it as shown also supplies Spring Boot and jOOQ. No password has a committed default. Production credentials and private keys must come from the platform secret store and must never be committed.

## Local secret gate

CI scans tracked files for hardcoded credential material. Enable the same gate before every local commit and push with:

```bash
git config core.hooksPath .githooks
```

The hooks invoke the scan before commit and again before push; CI independently scans all tracked file categories. No external scanner or API credential is required for this local baseline, while GitGuardian remains the remote security authority.

Set `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` to the Keycloak realm URL. Health and OpenAPI are public foundation surfaces; every other route is authenticated by default.

Swagger UI is available at `http://localhost:8080/swagger-ui.html`; liveness/readiness endpoints are below `/actuator/health`.

The current functional entry point is `POST /api/v1/bootstrap`, which returns
the initial authenticated user/business/installation state without performing
sync or other domain operations.
