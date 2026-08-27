# TINO Backend

Production-grade foundation for the TINO Android synchronization backend.

## Prerequisites

- Java 21
- Docker (for integration tests and local PostgreSQL)

## Verification

```bash
./gradlew clean build architecture migrations
```

M0 integration tests use Testcontainers and execute the technical Flyway migration and jOOQ connection against real PostgreSQL. Domain tables and RLS belong to M1 and are intentionally absent.

## Local runtime

```bash
./scripts/create-local-env.sh
set -a
. ./.env
set +a
docker compose up -d
./gradlew :app:bootRun
```

Compose runs only PostgreSQL and Keycloak. During development the Spring Boot application runs directly through Gradle for a shorter feedback loop.
If the default ports are occupied, use `TINO_POSTGRES_PORT=55432 TINO_KEYCLOAK_PORT=58081 docker compose up -d` and point the application environment at those ports.

`scripts/create-local-env.sh` generates local credentials at runtime into the ignored, mode-`600` `.env` file and refuses to overwrite an existing file. Compose reads it automatically; exporting it as shown also supplies Spring Boot and jOOQ. No password has a committed default. Production credentials and private keys must come from the platform secret store and must never be committed.

## Local secret gate

CI scans tracked files for hardcoded credential material. Enable the same gate before every local commit and push with:

```bash
git config core.hooksPath .githooks
```

The hooks invoke the scan before commit and again before push; CI independently scans all tracked file categories. No external scanner or API credential is required for this local baseline, while GitGuardian remains the remote security authority.

Set `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` to the Keycloak realm URL. Health and OpenAPI are public foundation surfaces; every other route is authenticated by default.

Swagger UI is available at `/swagger-ui.html`; liveness/readiness endpoints are below `/actuator/health`.
