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
docker compose up -d
export SPRING_DATASOURCE_PASSWORD='<local-runtime-value>'
export SPRING_FLYWAY_PASSWORD='<local-migration-value>'
./gradlew :app:bootRun
```

Compose runs only PostgreSQL and Keycloak. During development the Spring Boot application runs directly through Gradle for a shorter feedback loop.
If the default ports are occupied, use `TINO_POSTGRES_PORT=55432 TINO_KEYCLOAK_PORT=58081 docker compose up -d` and point the application environment at those ports.

The credentials in `compose.yaml` and `docker/postgres/init.sql` are disposable local-development values only. Production credentials and private keys must be injected at runtime and must never be committed.
The Spring Boot datasource and Flyway passwords have no committed defaults and must be supplied through their environment variables.

## Local secret gate

CI scans tracked application sources for hardcoded credential literals. Enable the same gate before every local commit with:

```bash
git config core.hooksPath .githooks
```

The hook invokes `./scripts/secret-scan.sh --cached`; no external scanner or API credential is required.

Set `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` to the Keycloak realm URL. Health and OpenAPI are public foundation surfaces; every other route is authenticated by default.

Swagger UI is available at `/swagger-ui.html`; liveness/readiness endpoints are below `/actuator/health`.
