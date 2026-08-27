# M1 Evidence — Database Foundation

Recorded: 2026-08-26 (America/Fortaleza)
Repository: `HEINRICK7/backend-tino`
Milestone: `M1 — DATABASE FOUNDATION` (authorized)
Branch: `sdd/m1-database-foundation`
Base checkpoint: `72afcf3e373401a283538df75a47281d7eeb8184`
Verified implementation revision: `56014c0a70a740787f88d1886c3cbe8d47e6cc34`
M2 AUTHORIZED: **NO**

## Verdict

```text
M1 STATUS: PASS
Architecture: PASS
Build: PASS
Tests: PASS
Flyway: PASS
jOOQ: PASS
RLS: PASS
Tenant Leakage: PASS
Database Privileges: PASS
Git: PASS
Scope Leakage: NONE
```

## Environment and dependency versions

| Component | Version/evidence |
|---|---|
| Java | OpenJDK `21.0.11` |
| Gradle | `8.14.4`, Kotlin DSL; wrapper committed |
| Docker Engine | `29.1.3` |
| PostgreSQL | Testcontainers/Compose image `postgres:17-alpine`; image id `sha256:1bea307dfb3ee30541a7acf7de14b58bcd6948da98e5d31a04c627c4d35ec64b` |
| Spring Boot | `4.1.1` BOM |
| Flyway | `12.4.0` |
| jOOQ | `3.21.7` |
| Testcontainers | `2.0.5` |
| HikariCP | `7.0.2` (test pool) |

The versions were read from `java -version`, `./gradlew --version`, Docker image inspection, and the Gradle dependency report. Spring-managed versions remain BOM/catalog controlled; no manual Spring version was added.

## Implementation and changed files

- `shared/kernel/src/main/java/com/tino/backend/shared/kernel/BusinessId.java` — framework/database-agnostic UUID tenant value object.
- `shared/kernel/src/main/java/com/tino/backend/shared/kernel/TenantContextExecutor.java` — small `execute(BusinessId, Supplier<T>)` contract with no transaction/JDBC/jOOQ types.
- `shared/kernel/src/main/java/com/tino/backend/shared/kernel/UuidGenerator.java` and `UuidV7Generator.java` — application-side UUID v7 contract/implementation with epoch-millisecond ordering, version 7, RFC variant, and random bits.
- `shared/infrastructure/src/main/java/com/tino/backend/shared/infrastructure/tenant/PostgresTenantContextExecutor.java` — Spring/JDBC adapter using `READ COMMITTED`, `REQUIRES_NEW`, and transaction-local `set_config`.
- `shared/infrastructure/src/test/java/com/tino/backend/shared/infrastructure/tenant/PostgresTenantContextExecutorTest.java` — real PostgreSQL 17 fixture, non-owner `tino_app`, forced RLS, one-connection Hikari pool, reset/isolation/jOOQ/privilege proofs.
- `shared/kernel/src/test/java/com/tino/backend/shared/kernel/UuidV7GeneratorTest.java` — UUID v7 validity, variant, uniqueness, and ordering proof.
- `app/src/test/java/com/tino/backend/FoundationPostgresTest.java` — empty-database Flyway migration count/validate and real PostgreSQL jOOQ proof.
- `app/src/test/java/com/tino/backend/M1ArchitectureTest.java` — source/runtime boundary proof for jOOQ and forbidden ORM persistence.
- `app/src/main/resources/application.yml` and `app/src/test/java/com/tino/backend/M1ConfigurationTest.java` — explicit `tino_migrator` Flyway defaults with environment overrides, while runtime datasource defaults remain `tino_app`, plus focused configuration proof.
- `shared/kernel/build.gradle.kts` and `shared/infrastructure/build.gradle.kts` — M1 test wiring, aligned JUnit Platform launcher, Testcontainers, and test-only Hikari dependency.

No migration, domain module source, Compose file, Keycloak file, or `docker/postgres/init.sql` changed. Existing `V0__foundation.sql` remains immutable and contains only `SELECT 1`.

## Database identities and privileges

Compose PostgreSQL was started on host port `55432` to avoid unrelated host-port users. Exact inspection commands were:

```bash
TINO_POSTGRES_PORT=55432 docker compose up -d postgres
docker exec backend-tino-postgres-1 pg_isready -U tino_admin -d tino
docker exec backend-tino-postgres-1 psql -U tino_admin -d tino -X -At -c "select rolname, rolsuper, rolbypassrls, rolcreatedb, rolcreaterole, rolcanlogin from pg_roles where rolname in ('tino_app','tino_migrator') order by rolname;"
docker exec backend-tino-postgres-1 psql -U tino_admin -d tino -X -At -c "select nspname, has_schema_privilege('tino_app', nspname, 'USAGE'), has_schema_privilege('tino_app', nspname, 'CREATE') from pg_namespace where nspname='public';"
TINO_POSTGRES_PORT=55432 docker compose down
```

Observed output:

```text
tino_app|f|f|f|f|t
tino_migrator|f|f|f|f|t
public|t|f
```

The integration fixture independently creates `tino_app` with `NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE NOINHERIT`, grants only database connect/schema usage and fixture DML, and asserts the role flags, schema/table ownership, no `CREATE` privilege, `current_user = tino_app`, and app-role `CREATE TABLE` rejection. The admin/superuser is used only for fixture setup/metadata; all RLS DML runs as the non-owner app role.

## Tenant context and RLS

`PostgresTenantContextExecutor` requires a resolved non-null `BusinessId` and operation, opens a `REQUIRES_NEW` `READ COMMITTED` transaction, then executes:

```sql
select set_config('app.business_id', ?, true)
```

The `true` flag is transaction-local. The test uses a `TransactionAwareDataSourceProxy` for jOOQ and a one-connection Hikari pool, proving the setting and reset on the same physical connection. The test-only `tenant_probe(id uuid primary key, business_id uuid not null, value varchar(200) not null)` fixture has `ENABLE ROW LEVEL SECURITY`, `FORCE ROW LEVEL SECURITY`, and a policy with both `USING` and `WITH CHECK`. Its policy uses `NULLIF(current_setting('app.business_id', true), '')::uuid`; PostgreSQL exposes an empty custom-GUC placeholder after reset, and `NULLIF` preserves fail-closed behavior for both empty and SQL-NULL absence. No persistent `SET` is used.

## TEST-M1-001..015 results

| Test | Concrete proof | Result |
|---|---|---|
| 001 | `FoundationPostgresTest`: empty PostgreSQL container, exactly `1` Flyway migration | PASS |
| 002 | Same test calls `flyway.validate()` after migration | PASS |
| 003 | Actual role flags, schema/table ownership, forced RLS, current user, DDL denial | PASS |
| 004 | A/B forced-RLS read isolation under `tino_app` | PASS |
| 005 | A-context insert with B key rejected; no row remains | PASS |
| 006 | A-context update moving A row to B rejected; original key remains | PASS |
| 007 | A-context delete targeting B affects zero rows; B remains | PASS |
| 008 | No-context read sees zero rows; no-context insert rejected | PASS |
| 009 | Context present in operation and blank/absent after commit | PASS |
| 010 | Context present in operation and blank/absent after rollback | PASS |
| 011 | 12 repeated A/B/no-context cycles on one pooled connection | PASS |
| 012 | jOOQ `select 1` through transaction-aware datasource | PASS |
| 013 | Source scan proves no jOOQ package/type in kernel/modules/app main contracts | PASS |
| 014 | 1,000 UUIDs: version `7`, variant `2`, unique, sorted | PASS |
| 015 | Source/runtime audit has no hibernate-core, Spring Data JPA, Jakarta Persistence, or ORM | PASS |

Additional M1 configuration proof: `M1ConfigurationTest.keepsRuntimeAndFlywayDatabaseIdentitiesSeparateByDefault` verifies datasource defaults remain `tino_app` while Flyway defaults are explicitly `tino_migrator`, and both `SPRING_FLYWAY_USER`/`SPRING_FLYWAY_PASSWORD` overrides remain present; **PASS**.

Exact test commands and outcomes:

```bash
./gradlew :app:test --tests com.tino.backend.FoundationPostgresTest --tests com.tino.backend.M1ArchitectureTest --rerun-tasks --no-daemon --console=plain
# BUILD SUCCESSFUL; all 3 selected tests passed

./gradlew :app:test --tests com.tino.backend.M1ConfigurationTest --rerun-tasks --no-daemon --console=plain
# BUILD SUCCESSFUL; configuration identity-separation test passed

./gradlew :shared:kernel:test --tests com.tino.backend.shared.kernel.UuidV7GeneratorTest --rerun-tasks --no-daemon --console=plain
# BUILD SUCCESSFUL; UUID v7 test passed

./gradlew :shared:infrastructure:test --tests com.tino.backend.shared.infrastructure.tenant.PostgresTenantContextExecutorTest --rerun-tasks --no-daemon --console=plain
# BUILD SUCCESSFUL; all 10 PostgreSQL fixture tests passed
```

## Required gates

```bash
./gradlew clean build architecture --rerun-tasks --no-daemon --console=plain
# BUILD SUCCESSFUL in 39s; 34 actionable tasks, all executed

./gradlew migrations --rerun-tasks --no-daemon --console=plain
# BUILD SUCCESSFUL; root migration task and app PostgreSQL test passed

TINO_POSTGRES_PORT=55432 docker compose up -d postgres
JOOQ_JDBC_URL=jdbc:postgresql://127.0.0.1:55432/tino JOOQ_JDBC_USER=tino_migrator JOOQ_JDBC_PASSWORD=tino_migrator ./gradlew :shared:infrastructure:jooqCodegen --rerun-tasks --no-daemon --console=plain
TINO_POSTGRES_PORT=55432 docker compose down
# BUILD SUCCESSFUL; jOOQ codegen completed against PostgreSQL 17

git diff --check
# PASS
```

`jooqCodegen` excluded the sole technical `flyway_schema_history` table, so no domain records were generated. The Gradle build uses the existing M0 compiler settings (`-Xlint:all -Werror`); no gate was disabled or weakened.

Final JUnit XML totals from the full gate: app `6` tests, shared infrastructure `10` tests, and shared kernel `1` test; all report `failures=0` and `errors=0`.

## Independent supervisor verification

After inspecting every changed and new file, the supervising session independently reran the acceptance gates:

- `./gradlew clean build architecture --rerun-tasks --no-daemon --console=plain` — **PASS**, `BUILD SUCCESSFUL in 40s`, 34 actionable tasks (33 executed, one root lifecycle task up-to-date), including Spring Modulith verification.
- Targeted app M1 suites — **PASS**, four tests, zero skipped/failures/errors.
- Targeted shared-kernel UUID suite — **PASS**, one test, zero skipped/failures/errors.
- Targeted shared-infrastructure tenant/RLS suite — **PASS**, ten tests, zero skipped/failures/errors.
- `./gradlew migrations --rerun-tasks --no-daemon --console=plain` — **PASS**, `BUILD SUCCESSFUL in 31s` against disposable PostgreSQL.
- Compose PostgreSQL on port `55432` — **PASS**; `tino_app`/`tino_migrator` flags were independently reconfirmed as `f|f|f|f` for superuser, bypass RLS, create database, and create role. `tino_app` retained schema `USAGE` and lacked schema `CREATE`.
- jOOQ code generation against that Compose PostgreSQL — **PASS**, `BUILD SUCCESSFUL in 18s`; no generated domain Java files.
- Dependency, source, migration, secret, build-artifact, and M1+ scope scans — **PASS**; scope leakage `NONE`.
- Validation Compose services/network were shut down after the gate; `docker compose ps` returned no project services.

## Dependency and scope audits

```bash
./gradlew :app:dependencies --configuration runtimeClasspath --no-daemon --console=plain | rg -i 'hibernate-core|spring-data-jpa|jakarta.persistence|org\.hibernate\.orm' || echo 'forbidden ORM runtime dependencies: none'
# forbidden ORM runtime dependencies: none

./gradlew :app:dependencies --configuration testRuntimeClasspath --no-daemon --console=plain | rg -i 'hibernate-core|spring-data-jpa|spring-data-redis|kafka-clients|spring-kafka|amqp|rabbitmq|lettuce-core|jedis' || echo 'forbidden ORM/Redis/Kafka/Rabbit test runtime dependencies: none'
# forbidden ORM/Redis/Kafka/Rabbit test runtime dependencies: none

if rg -n -i 'hibernate-core|spring-data-jpa|jakarta\.persistence|org\.hibernate\.orm' --glob '!**/build/**' --glob '!docs/**' --glob '!app/src/test/java/com/tino/backend/M1ArchitectureTest.java' shared app/src/main; then echo 'forbidden source references found'; else echo 'forbidden ORM source references: none'; fi
# forbidden ORM source references: none

if rg -n -i 'CREATE TABLE|CREATE POLICY' --glob '!**/build/**' app/src/main/resources/db/migration; then echo 'domain DDL migration found'; else echo 'committed migrations contain no table/policy DDL'; fi
# committed migrations contain no table/policy DDL

if rg -n -i 'create table (users|businesses|memberships|profiles|devices|customers|credit|ledger|payments|pix|reconciliation)|@(RestController|Controller)|/api/v1|/v1/sync|bootstrap' --glob '*.java' --glob '*.sql' --glob '!**/build/**' --glob '!docs/**' .; then echo 'forbidden M1+ implementation matches found'; else echo 'M1+ Java/SQL scope leakage: none'; fi
# M1+ Java/SQL scope leakage: none
```

The only DDL source excluded from that implementation scan is the explicitly test-only `tenant_probe` fixture and its expected app-role DDL-denial assertion; neither is a migration or functional table. `hibernate-validator` remains benign validation-only dependency.

## Deviations and supervisor notes

1. Shared kernel tests initially lacked a matching JUnit Platform launcher under Spring Boot 4/JUnit 6; the owned build scripts now add the versionless BOM-managed launcher as test runtime wiring.
2. PostgreSQL reports the reset custom GUC as an empty placeholder; the fixture policy's `NULLIF` and tests prove no-context fail-closed behavior.
3. The bounded configuration correction changes only Flyway's local defaults to `tino_migrator`/`tino_migrator`; runtime datasource defaults remain `tino_app`/`tino_app`, and the two Flyway environment overrides are preserved. `M1ConfigurationTest` proves the separation.
4. The verified implementation is commit `56014c0a70a740787f88d1886c3cbe8d47e6cc34`; the evidence-only follow-up revision is reported in the final milestone output. No M2 behavior was started.

**M2 AUTHORIZED: NO**
