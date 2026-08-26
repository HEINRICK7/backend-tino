# M0 Evidence — Project Foundation

Recorded: 2026-08-26 (America/Fortaleza)  
Repository: `HEINRICK7/backend-tino`  
Branch: `main`  
Initial revision: `50a46f5320940af5ee066be2d33ab6ac701c2782`  
Remote: `git@github.com:HEINRICK7/backend-tino.git`

## Verdict

```text
M0 STATUS: PASS
Architecture: PASS
Build: PASS
Tests: PASS
Flyway: PASS
jOOQ: PASS
PostgreSQL Compose: PASS
Keycloak Compose: PASS
Git/GitHub: PASS
Scope Leakage: NONE

M1 AUTHORIZED: NO
```

## Environment

| Component | Evidence |
|---|---|
| Java | OpenJDK `21.0.11` |
| Gradle | `8.14.4`, Kotlin DSL; wrapper committed |
| Docker | `29.1.3` |
| Docker Compose | plugin v2 `2.40.3`, installed in the user's Docker CLI plugin directory after system package installation required an unavailable sudo password |
| Spring Boot | `4.1.1` |
| Spring Framework | `spring-context:7.0.9`, selected by the Spring Boot BOM |
| PostgreSQL | `17-alpine`; runtime reported PostgreSQL `17.11` |
| Keycloak | `26.3`, realm `tino` imported |

## Created topology and dependency map

Gradle projects verified by `./gradlew projects`:

```text
app
modules:identity
modules:business
modules:device
modules:sync
shared:kernel
shared:infrastructure
build-logic (included build)
```

`app` composes the empty functional/shared projects and owns the Spring Boot launcher/foundation adapters. Functional projects are empty architectural containers. The Spring Boot BOM is an `api(platform(...))` constraint in every Java project. jOOQ is configured only in `shared:infrastructure`; no jOOQ type exists in domain/application code (none exists in M0).

## Automated gates

### Final clean build and architecture

Command:

```bash
./gradlew clean build architecture --rerun-tasks --no-daemon --console=plain
```

Result: **PASS**, `BUILD SUCCESSFUL in 47s`, 32/32 tasks executed. `:architecture` ran after `:app:test` and `ModularityTest` called `ApplicationModules.of(...).verify()`.

### Mandatory tests without cache

Command:

```bash
./gradlew :app:test \
  --tests com.tino.backend.ApplicationFoundationTest \
  --tests com.tino.backend.FoundationPostgresTest \
  --tests com.tino.backend.ModularityTest \
  --rerun-tasks --no-daemon --console=plain
```

Result: **PASS**, three tests, zero skipped/failures/errors:

- `TEST-M0-001` context loads: `ApplicationFoundationTest`.
- `TEST-M0-002` Flyway migrates empty PostgreSQL: `FoundationPostgresTest` and runtime proof.
- `TEST-M0-003` jOOQ accesses Testcontainers PostgreSQL: `FoundationPostgresTest`.
- `TEST-M0-004` Modulith verification: `ModularityTest`.
- `TEST-M0-005` public Actuator health responds `200/UP`: `ApplicationFoundationTest`.
- `TEST-M0-006` selected domain skeleton packages: **PASS (not present by design)**; functional projects contain no source files.

Test XML timestamps were `2026-08-26T22:57:31Z` through `22:57:35Z`; each suite reports `tests=1`, `failures=0`, `errors=0`.

### jOOQ generation

Command against the real Compose PostgreSQL:

```bash
JOOQ_JDBC_URL=jdbc:postgresql://127.0.0.1:55432/tino \
JOOQ_JDBC_USER=tino_migrator \
JOOQ_JDBC_PASSWORD=<local-disposable> \
./gradlew :shared:infrastructure:jooqCodegen --no-daemon --console=plain
```

Result: **PASS**, `:shared:infrastructure:jooqCodegen` executed successfully. It generated no domain records because the only public table is `flyway_schema_history`, explicitly excluded; M1 owns domain generation.

## Local runtime gates

### Compose

`docker compose version` returned v2.40.3. `docker compose config --quiet` passed and `docker compose config --services` returned exactly:

```text
keycloak
postgres
```

The first start using default port 5432 failed because an unrelated, already-running container owned that host port. No unrelated resource was stopped or modified. Compose ports were made configurable while preserving defaults, and validation used:

```bash
TINO_POSTGRES_PORT=55432 TINO_KEYCLOAK_PORT=58081 docker compose up -d
```

Final `docker compose ps` before shutdown showed PostgreSQL `healthy` on `55432` and Keycloak up on `58081`. `pg_isready` returned `accepting connections`. The realm endpoint `http://127.0.0.1:58081/realms/tino` returned HTTP 200 and realm metadata.

Database role query result:

```text
tino_app:false:false
tino_migrator:false:false
```

The boolean fields are `rolsuper` and `rolbypassrls`; both roles satisfy `NOSUPERUSER/NOBYPASSRLS`.

After all checks, the exact alternative-port command `docker compose down` removed the project's two containers and network. A subsequent `docker compose ps` returned no services. The named development volume was intentionally preserved; no user data outside this project was touched.

### Spring Boot / Flyway / HTTP

The application ran through `./gradlew :app:bootRun` (not Compose) using the Compose PostgreSQL/Keycloak. Evidence:

- Spring Boot 4.1.1 started on Java 21 in 4.466 seconds without startup error.
- DataSource established a Hikari connection to `jdbc:postgresql://127.0.0.1:55432/tino` (PostgreSQL 17.11).
- Flyway validated and applied exactly one migration, `0 - foundation`, from an empty schema.
- Database inspection returned `0:true` and exactly one public table: `flyway_schema_history`.
- `GET /actuator/health` returned HTTP 200 with `{"status":"UP"}` and liveness/readiness groups.
- The response contained a generated `X-Correlation-Id`; the filter validates bounded client values and clears MDC in `finally`.
- `GET /openapi` returned HTTP 200, OpenAPI 3.1.0, and `"paths":{}` (no functional endpoint).
- The OIDC discovery document exposed issuer `http://127.0.0.1:58081/realms/tino` and the realm JWKS URI. A syntactically valid but incorrectly signed bearer token against a protected Actuator route returned HTTP 401; Resource Server logs show successful HTTP 200 discovery and JWKS retrieval. No token or credential value appeared in application logs.

## Foundation capability evidence

- Actuator and Micrometer: health/data-source observation active at runtime.
- OpenTelemetry: official starter and Micrometer OTel bridge present; SDK export is disabled by safe local default and can be enabled through `OTEL_SDK_DISABLED=false` with runtime exporter configuration.
- Resilience4j: Spring Boot 4 auto-configurations for circuit breaker/bulkhead loaded; no external integration/use case exists.
- OpenAPI: springdoc endpoint active with empty path set.
- Security: default-deny Resource Server; health/OpenAPI are explicitly public; there is no permit-all development switch.
- Correlation: bounded/sanitized `X-Correlation-Id`, MDC lifecycle, response propagation.
- CI foundation: GitHub Actions validates Compose and executes clean build/architecture on Java 21.

## Dependency and scope audit

Runtime dependency report and source scans proved absence of:

- Hibernate ORM/JPA (`hibernate-core`, Jakarta Persistence, Spring Data JPA);
- Redis clients/Spring Data Redis;
- Kafka clients/Spring Kafka/instrumentation;
- RabbitMQ/AMQP;
- domain repositories, controllers, endpoints, handlers, or use cases.

`hibernate-validator:9.1.3.Final` is present solely as the Bean Validation provider from the Spring validation starter; it is not ORM/persistence. The generic OpenTelemetry starter initially introduced Kafka instrumentation-only modules transitively; they were explicitly excluded, and the repeated dependency audit returned `none` for all forbidden categories.

Scope scans found:

- no `CREATE TABLE`, `ALTER TABLE`, or `CREATE POLICY` in M0 migrations;
- no `@Controller`/`@RestController`, `/api/v1`, or `/v1/sync` in source;
- no source file at all under the four functional module projects;
- only launcher and foundation security/correlation code in `app`.

Therefore no M1+ feature remains in this delivery.

## Version control and security review

Git was initialized during M0, branch renamed to `main`, and `origin` configured without replacing an existing remote. Before `git add .`, scans covered `.env`, passwords/tokens/client secrets, private keys/certificates, IDE files, caches, builds, logs, and temporaries. High-confidence secret patterns returned none. The staged-file review contained only project sources/docs/wrapper; ignored status proved `.gradle/` and all project `build/` directories were excluded.

The only committed credentials are the documented disposable local-development values for Compose roles/admin. Production credentials remain runtime-only. Initial commit `50a46f5320940af5ee066be2d33ab6ac701c2782` used message `chore: bootstrap TINO backend foundation` and was pushed successfully to `origin/main`.

## Deviations detected and resolved

1. **Spring version incompatibility** — `spring-context`, `spring-tx`, and `spring-jdbc` were initially pinned to nonexistent stable `7.1.2`; dependency resolution failed. Manual pins were removed, `spring-boot-dependencies:4.1.1` became the authority in every project/configuration, and `dependencyInsight` proved `spring-context:7.0.9`. The next clean build passed.
2. **Testcontainers 2 API** — legacy generic `org.testcontainers.containers.PostgreSQLContainer` produced deprecation warnings under `-Werror`; tests moved to non-generic `org.testcontainers.postgresql.PostgreSQLContainer` without relaxing compilation.
3. **Flyway Boot 4 activation** — core libraries alone did not create schema history at runtime. The BOM-managed official `spring-boot-starter-flyway` was added; empty-schema runtime then applied v0 successfully.
4. **jOOQ codegen driver version** — the isolated `jooqCodegen` configuration did not inherit `api` constraints. The Spring Boot BOM was added directly to that configuration (no manual driver version); generation passed.
5. **Port 5432 collision** — an unrelated container occupied the default. Configurable host ports were added, validation used 55432/58081, and the unrelated workload was preserved.
6. **OpenTelemetry Kafka instrumentation** — generic starter transitives contained Kafka instrumentation modules despite no Kafka client/use. They were excluded and the forbidden-dependency audit passed.
7. **Milestone leakage removed** — early draft domain migrations (`users`, businesses, memberships, devices, sync tables/RLS/outbox) and domain helper/adapters were removed after the milestone protocol superseded the original broad foundation request. M0 now has only v0 technical migration.
8. **Compose scope corrected** — an early application service/Dockerfile draft was removed. Compose contains PostgreSQL and Keycloak only; the backend runs by Gradle.

## Known gaps (intentional M0 boundaries)

There is no persisted identity, membership authorization, business/device/bootstrap/sync endpoint, tenant table, RLS policy, domain jOOQ generation, or external-effect worker. These are not M0 failures; they require explicit later milestone authorization. The future milestone documents remain `DRAFT — NOT AUTHORIZED`.

## Completion

Every M0 acceptance criterion has direct command/runtime evidence. No gate was disabled or weakened.

**M0 STATUS: PASS**  
**M1 AUTHORIZED: NO**
