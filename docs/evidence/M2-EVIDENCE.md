# M2 Evidence — Identity & Security Foundation

Recorded: 2026-08-27 (America/Fortaleza)
Repository: `HEINRICK7/backend-tino`
Branch: `sdd/m2-identity-security`
Base commit: `2f338a443291ab329dd43185c56fd58bf6b6e761` (`main`, M2 `AUTHORIZED`)
Implementation commit: `dc25fa4` (`feat: implement M2 identity and security foundation`)
Final evidence commit: pending supervisor finalization
M3 authorized: **NO**

This document records implementation verification performed by Luna. Terra/root
must independently review the diff, rerun critical gates, and decide the final
milestone verdict. No credentials, token values, private keys, or bearer values
are recorded here.

## Verdict at implementation handoff

```text
M2 implementation gates: PASS
Architecture: PASS
Build: PASS
Tests: PASS
Identity: PASS
Authentication: PASS
Concurrency: PASS
Flyway: PASS
jOOQ: PASS
Database: PASS
Privacy: PASS
Security boundaries: PASS
Modulith: PASS
Scope leakage: NONE
Supervisor independent verification: PENDING
M3 AUTHORIZED: NO
```

## Scope and changed files

The implementation is limited to the authorized M2 identity/security files.
The pre-existing milestone authorization is preserved; no milestone, ADR,
baseline, or published migration was rewritten.

Production and build files:

- `app/src/main/java/com/tino/backend/foundation/SecurityFoundationConfiguration.java` — Resource Server converter composition, lazy Nimbus decoder, issuer/timestamp/mandatory-sub validators, and explicit client policy.
- `app/src/main/resources/application.yml` — `tino.security.oidc.client-id`, defaulting to the existing `tino-android` client; database secrets remain environment-only.
- `app/src/main/resources/db/migration/V1__identity_users.sql` — immutable M2 users schema and runtime grant.
- `modules/identity/build.gradle.kts` — Resource Server adapter dependency and JUnit launcher.
- `shared/kernel/build.gradle.kts` — compile-only Modulith annotation dependency needed to expose the existing UUID foundation named interface.
- `shared/kernel/src/main/java/com/tino/backend/shared/kernel/package-info.java` — named interface `foundation`; this is the narrowly necessary Modulith wiring for the existing M1 `UuidGenerator`/`UuidV7Generator` reuse.

Identity production sources:

- `modules/identity/src/main/java/com/tino/backend/identity/domain/model/User.java`
- `modules/identity/src/main/java/com/tino/backend/identity/domain/model/UserId.java`
- `modules/identity/src/main/java/com/tino/backend/identity/domain/model/ExternalSubject.java`
- `modules/identity/src/main/java/com/tino/backend/identity/domain/model/UserStatus.java`
- `modules/identity/src/main/java/com/tino/backend/identity/application/port/in/AuthenticatedPrincipal.java`
- `modules/identity/src/main/java/com/tino/backend/identity/application/port/out/UserRepository.java`
- `modules/identity/src/main/java/com/tino/backend/identity/application/port/out/ExternalSubjectAlreadyExistsException.java`
- `modules/identity/src/main/java/com/tino/backend/identity/application/port/out/UserPersistenceException.java`
- `modules/identity/src/main/java/com/tino/backend/identity/application/exception/InvalidAuthenticatedPrincipalException.java`
- `modules/identity/src/main/java/com/tino/backend/identity/application/exception/DisabledUserException.java`
- `modules/identity/src/main/java/com/tino/backend/identity/application/exception/UserResolutionException.java`
- `modules/identity/src/main/java/com/tino/backend/identity/application/usecase/ResolveAuthenticatedUser.java`
- `modules/identity/src/main/java/com/tino/backend/identity/adapter/in/security/SpringSecurityPrincipalMapper.java`
- `modules/identity/src/main/java/com/tino/backend/identity/adapter/in/security/AuthenticatedPrincipalAuthenticationToken.java`
- `modules/identity/src/main/java/com/tino/backend/identity/adapter/in/security/AuthenticatedPrincipalJwtAuthenticationConverter.java`
- `modules/identity/src/main/java/com/tino/backend/identity/adapter/in/security/IdentitySecurityConfiguration.java`
- `modules/identity/src/main/java/com/tino/backend/identity/adapter/out/persistence/JooqUserRepository.java`

Tests and fixtures:

- `app/src/test/java/com/tino/backend/M2PostgresTestContainer.java` — PostgreSQL 17 fixture; roles and passwords are generated in memory at runtime.
- `app/src/test/java/com/tino/backend/M2IdentityPostgresTest.java`
- `app/src/test/java/com/tino/backend/M2SecurityBoundaryTest.java`
- `app/src/test/java/com/tino/backend/M2ArchitecturePrivacyScopeTest.java`
- `app/src/test/java/com/tino/backend/ApplicationFoundationTest.java` — M2 role-separated context fixture.
- `app/src/test/java/com/tino/backend/FoundationPostgresTest.java` — M2 migration count and runtime-role fixture.
- `app/src/test/java/com/tino/backend/M1ArchitectureTest.java` — retains the M1 inner-contract scan while allowing the authorized M2 persistence adapter to use jOOQ.
- `modules/identity/src/test/java/com/tino/backend/identity/application/usecase/ResolveAuthenticatedUserTest.java`
- `modules/identity/src/test/java/com/tino/backend/identity/adapter/in/security/SpringSecurityPrincipalMapperTest.java`

No `m2-roles.sql` or other credential-bearing fixture was restored. The two
pre-existing stashes remain untouched and preserved.

## Identity model and application

`User` is a framework-free record containing exactly `UserId`,
`ExternalSubject`, `UserStatus`, `createdAt`, and `updatedAt`. `UserId` wraps a
UUID. `ExternalSubject` is a nonblank opaque string and does not assume UUID,
email, phone, or username format. `UserStatus` contains only `ACTIVE` and
`DISABLED`. Timestamps are `Instant`.

`AuthenticatedPrincipal` contains only `ExternalSubject`. Domain/application
source imports contain no Spring, Spring Security, jOOQ, JDBC, Keycloak, JWT,
or persistence exception types.

`ResolveAuthenticatedUser` validates the principal, finds by external subject,
creates an `ACTIVE` user using the existing M1 `UuidV7Generator`, and returns
the existing user on repetition. A unique-race exception is translated by the
adapter into the application port exception; the use case performs a fresh
lookup and returns the committed winner. A `DISABLED` row raises
`DisabledUserException`. There is no JVM lock, distributed lock, cache, or
external call.

`UserRepository` is specific to this use case and exposes only
`findByExternalSubject` and `insert`. `JooqUserRepository` is the only jOOQ
adapter; it maps records to the domain and translates SQLSTATE `23505` to the
port-level unique-race exception. jOOQ/generated types and SQL exceptions do
not cross the application/domain boundary.

## Database migration and privileges

`V1__identity_users.sql` is new and V0 remains byte-for-byte unchanged. It
creates only:

```sql
public.users(
  id UUID PRIMARY KEY,
  external_subject VARCHAR(255) NOT NULL UNIQUE,
  status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
)
```

The migration grants only `SELECT, INSERT` on `public.users` to `tino_app`.
There is no `business_id`, tenant RLS, personal claim, password, token, or
speculative functional table. `tino_migrator` owns migration execution;
runtime jOOQ operations use `tino_app`. The PostgreSQL integration test proves
`SELECT`/`INSERT` are available and `UPDATE`/`DELETE` are not.

## JWT/OIDC policy

Spring Security remains the inbound adapter. The validated `Jwt` is mapped to
`AuthenticatedPrincipal` using only `Jwt.getSubject()`. Null/blank `sub`
returns no principal and the authentication converter fails closed. The
authentication token stores the internal principal and returns `null` from
`getCredentials()`; it does not retain the bearer value.

The Resource Server decoder verifies Nimbus signature plus a composed validator
for issuer and timestamps, a required nonblank `sub`, and this explicit client
contract:

```text
configured client is accepted when (aud contains client-id) OR (azp equals client-id)
```

The configured default is `tino-android`. A cryptographically valid token with
the wrong issuer or with neither matching audience nor authorized party is
rejected. OIDC discovery is lazy so public health/OpenAPI startup does not
require an IdP connection. Only health and OpenAPI/Swagger paths are public;
other requests remain authenticated. No login, registration, user CRUD, or
business endpoint was added.

## Environment and versions

Observed on 2026-08-27:

| Component | Version/evidence |
|---|---|
| Java | OpenJDK `21.0.12` |
| Gradle | `8.14.4` (Kotlin `2.0.21`) |
| Spring Boot | `4.1.1` BOM authority |
| Flyway | `12.4.0` selected by BOM |
| jOOQ runtime | `3.21.7` selected by BOM |
| jOOQ codegen plugin | `3.20.10` existing configured plugin |
| Testcontainers | `2.0.5` |
| PostgreSQL test image | `postgres:17-alpine`, server reported `17.11` |
| Docker | `29.1.3` |
| Docker Compose | `v2.40.3` |

## Commands and actual results

Initial safety and state checks:

```text
git fetch --all --prune                         PASS
git status --short --branch                     main clean before branch work
git branch -avv                                 origin/main = 2f338a4
./scripts/secret-scan.sh                        Secret scan passed.
git stash list                                  stash@{0} and stash@{1} present
git stash show --stat/--patch (read-only)        PASS; neither stash applied
```

The branch was fast-forwarded from the existing proposal to authorized
`main` (`2f338a4`) without modifying `main`.

Implementation and targeted verification:

```text
./gradlew :modules:identity:test --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; 7 actionable tasks; 8 identity unit tests passed.

./gradlew :app:test --tests com.tino.backend.ApplicationFoundationTest --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; context/health test passed.

./gradlew :app:test --tests com.tino.backend.M2IdentityPostgresTest --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; 10 tests, failures=0, errors=0; PostgreSQL 17.11; 20 synchronized operations passed.

./gradlew :app:test --tests com.tino.backend.M2SecurityBoundaryTest --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; 8 tests, failures=0, errors=0.

./gradlew :app:test --tests com.tino.backend.M2ArchitecturePrivacyScopeTest --tests com.tino.backend.ModularityTest --tests com.tino.backend.M1ArchitectureTest --tests com.tino.backend.M1ConfigurationTest --tests com.tino.backend.FoundationPostgresTest --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; 13 tests, failures=0, errors=0.

./gradlew migrations --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; migration gate passed.

./gradlew clean build architecture --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 25s; 41 actionable tasks; build and architecture passed.
```

The final clean build XMLs reported zero failures/errors for all M2 and
regression suites:

```text
ApplicationFoundationTest                 1
FoundationPostgresTest                    1
M1ArchitectureTest                        2
M1ConfigurationTest                       1
M2ArchitecturePrivacyScopeTest             5
M2IdentityPostgresTest                    10
M2SecurityBoundaryTest                     8
ModularityTest                              1
ResolveAuthenticatedUserTest                5
SpringSecurityPrincipalMapperTest           3
M1/shared regression suites                 11
```

jOOQ generation was executed against a disposable Compose PostgreSQL 17.11
database after applying the M2 users DDL. The first disposable owner setup
made the metadata invisible to the migrator; the disposable table owner was
corrected to `tino_migrator` and the command was repeated:

```text
JOOQ_JDBC_URL=jdbc:postgresql://127.0.0.1:55432/tino \
JOOQ_JDBC_USER=tino_migrator \
JOOQ_JDBC_PASSWORD=<runtime-generated> \
./gradlew :shared:infrastructure:jooqCodegen --rerun-tasks --no-daemon --console=plain --info
PASS; PostgreSQL 17.11; Tables fetched 2 (1 included, 1 excluded); generated Users.java and 6 files; BUILD SUCCESSFUL.
```

The generated output is a build artifact and is not committed. The adapter
keeps generated metadata behind the persistence boundary.

## TEST-M2-001..027 mapping

Every required ID was exercised by a concrete test or audit:

| ID | Result and concrete proof |
|---|---|
| TEST-M2-001 | PASS — `SpringSecurityPrincipalMapperTest.mapsOnlyTheOpaqueSubjectFromAValidatedJwt`; `M2SecurityBoundaryTest.validSignedJwtWithSubjectAndAudienceReachesProtectedBoundary`. |
| TEST-M2-002 | PASS — `SpringSecurityPrincipalMapperTest.missingSubjectFailsClosedWithoutFallback`; `M2SecurityBoundaryTest.signedJwtWithoutSubjectIs401AndDoesNotReachIdentityUseCase`. |
| TEST-M2-003 | PASS — `M2SecurityBoundaryTest.invalidTokenIs401AndDoesNotReachIdentityUseCase`; probe count remained zero. |
| TEST-M2-004 | PASS — `M2SecurityBoundaryTest.expiredTokenIs401AndDoesNotReachIdentityUseCase`. |
| TEST-M2-005 | PASS — `M2SecurityBoundaryTest.wrongIssuerIs401EvenWhenSignatureIsValid`; probe count remained zero. |
| TEST-M2-006 | PASS — `M2SecurityBoundaryTest.wrongAudienceAndAuthorizedPartyAre401EvenWhenSignatureIsValid`; `authorizedPartyMaySatisfyExplicitClientContract` proves the documented OR branch. |
| TEST-M2-007 | PASS — `ResolveAuthenticatedUserTest.sameSubjectIsIdempotentAndNewUserIsActive`; `M2IdentityPostgresTest.sameSubjectIsIdempotentAndNewUsersDefaultToActive`. |
| TEST-M2-008 | PASS — `ResolveAuthenticatedUserTest.differentSubjectsResolveToDifferentUsers`; `M2IdentityPostgresTest.differentSubjectsProduceDifferentUsers`. |
| TEST-M2-009 | PASS — `M2IdentityPostgresTest.physicalUniqueConstraintTranslatesDuplicateInsert` against PostgreSQL UNIQUE. |
| TEST-M2-010 | PASS — `M2IdentityPostgresTest.twentyConcurrentFirstAccessesResolveOneUuidV7User`; 20 barrier-synchronized tasks, one row and one ID. |
| TEST-M2-011 | PASS — `ResolveAuthenticatedUserTest.disabledUserIsRejectedExplicitly`; `M2IdentityPostgresTest.disabledUserIsRejected`. |
| TEST-M2-012 | PASS — new JIT users are asserted `ACTIVE` in `sameSubjectIsIdempotentAndNewUsersDefaultToActive`. |
| TEST-M2-013 | PASS — UUID version 7 assertions in `timestampsRoundTripAsInstantAndUuidIsVersionSeven` and the concurrent test, using M1 `UuidV7Generator`. |
| TEST-M2-014 | PASS — `timestampsRoundTripAsInstantAndUuidIsVersionSeven`; metadata asserts both columns are `timestamp with time zone`. |
| TEST-M2-015 | PASS — `M2IdentityPostgresTest.migratesFromZeroAndFlywayValidatePasses`; `FoundationPostgresTest.flywayMigratesFromEmptyAndJooqWorksAgainstDisposablePostgres`; two migrations from empty PostgreSQL. |
| TEST-M2-016 | PASS — Flyway `validate()` in `migratesFromZeroAndFlywayValidatePasses`; V0 exact-content assertion in `M2ArchitecturePrivacyScopeTest.usersMigrationIsMinimalAndPublishedV0RemainsTechnicalOnly`. |
| TEST-M2-017 | PASS — all `M2IdentityPostgresTest` repository/use-case tests use real PostgreSQL 17.11 and jOOQ. |
| TEST-M2-018 | PASS — `M2ArchitecturePrivacyScopeTest.jooqIsConfinedToTheIdentityPersistenceAdapter`; only adapter source contains `DSLContext`/`DataAccessException`. |
| TEST-M2-019 | PASS — `M2ArchitecturePrivacyScopeTest.identityDomainAndApplicationDoNotImportFrameworkPersistenceOrProviderTypes`; live boundary suite passes. |
| TEST-M2-020 | PASS — same source audit and `KEYCLOAK_SCAN` returned `none`; no Keycloak classes are used. |
| TEST-M2-021 | PASS — `M2IdentityPostgresTest.usersIsGlobalIdentityAndHasNoPersonalOrTenantColumns`; no `business_id`, `relrowsecurity=false`. |
| TEST-M2-022 | PASS — `M2IdentityPostgresTest.runtimeUsesTinoAppAndMigrationUsesTinoMigrator`; runtime privileges and migration credentials are separated. |
| TEST-M2-023 | PASS — schema/privacy assertions and `M2ArchitecturePrivacyScopeTest.productionIdentityDoesNotPersistPersonalClaimsCredentialsOrTokens`. |
| TEST-M2-024 | PASS — production source/schema audit found no password hash, JWT, access-token, or refresh-token persistence. |
| TEST-M2-025 | PASS — `ModularityTest.verifiesModuleBoundaries`; `ApplicationModules.verify()` passes with the foundation named interface. |
| TEST-M2-026 | PASS — `./gradlew clean build architecture ...`; 41 actionable tasks successful. |
| TEST-M2-027 | PASS — `SCOPE_SCAN` returned `none`; M2 production sources contain no Business/Membership/Device/Bootstrap/Customer/Credit/Ledger/Payment/Pix/Reconciliation/Sync/WhatsApp implementation. |

## Audits

Dependency audit commands:

```text
./gradlew :app:dependencies --configuration runtimeClasspath --no-daemon --console=plain
forbidden-functional-dependencies: none

./gradlew :app:dependencies --configuration testRuntimeClasspath --no-daemon --console=plain
forbidden-functional-dependencies: none

./gradlew :modules:identity:dependencies --configuration runtimeClasspath --no-daemon --console=plain
forbidden-functional-dependencies: none
```

The audit covered Hibernate ORM/JPA, Spring Data JPA/Redis, Lettuce/Jedis,
Kafka, RabbitMQ/AMQP, and Keycloak admin/core dependencies. Hibernate Validator
is not ORM and was not treated as a finding.

Source/schema/privacy/scope checks:

```text
inner framework scan (identity domain/application)       none
Keycloak scan (identity/app production)                   none
ORM scan (shared/modules/app production)                  none
M2+ scope scan                                             none
./scripts/secret-scan.sh                                  Secret scan passed.
git diff --check                                           PASS
```

The static migration audit confirms only V1 adds `public.users`; V0 remains
unchanged. The PostgreSQL test confirms exactly five users columns, both
timestamp columns as `timestamp with time zone`, the status check, the unique
subject behavior, no users RLS, and no tenant/personal/credential columns.

## Git and security state

`stash@{0}` and `stash@{1}` are **PRESERVED**. Both were inspected read-only;
neither was applied, popped, dropped, or rewritten. The unsafe credential
candidate in `stash@{1}` was not recovered. Runtime test credentials are
generated in memory by `M2PostgresTestContainer` and are not logged, committed,
or evidenced.

Before implementation commit `dc25fa4`, the following gates passed:

```text
./scripts/secret-scan.sh       Secret scan passed.
git diff --check               PASS
staged-file review             32 authorized M2 files only
```

The implementation commit contains no generated build output, `.env`, private
key, certificate, password value, bearer token, JWT, or unexpected source.
No push or merge to `main` has been performed in this implementation handoff;
supervisor/root owns final Git verification and publication.

## Deviations and blockers

1. The existing M1 UUID types were not a Modulith named interface. Adding the
   compile-only annotation dependency and `foundation` package-info was the
   smallest required boundary declaration to reuse the approved M1 generator;
   `ModularityTest` passes. No second UUID mechanism was introduced.
2. M2 test roles are created by a PostgreSQL container lifecycle callback with
   runtime-generated credentials. This replaces the unsafe candidate SQL
   fixture and preserves the `tino_app`/`tino_migrator` least-privilege split.
3. The jOOQ codegen proof uses the existing infrastructure configuration and
   disposable PostgreSQL. Generated sources remain ignored build artifacts.

No specification conflict or architectural blocker was encountered. No M3
implementation was started.

## Final handoff

```text
MILESTONE: M2 — IDENTITY & SECURITY FOUNDATION
STATUS: PASS (implementation gates; supervisor final verification pending)
TEST-M2-001..027: PASS in mapping above
Stash@{0}: PRESERVED
Stash@{1}: PRESERVED
Branch: sdd/m2-identity-security
Implementation Commit: dc25fa4
Final Evidence Commit: pending supervisor finalization
Push: pending supervisor/root publication
Merge to main: NO
M3 AUTHORIZED: NO
STOP.
```
