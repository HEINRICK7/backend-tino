# M3 Evidence — Business & Membership

Recorded: 2026-08-27 (America/Fortaleza)
Repository: `HEINRICK7/backend-tino`
Milestone: `M3 — BUSINESS & MEMBERSHIP`
Branch: `feature/m3-business-membership`
Base develop: `36e7dcb92ed1fb085dedd0e2751f901360d1f824`
Proposal integration ancestor: `8faeae774b1007e4d7833edb048ee7ba9d6ee7ee`
Authorization commit: `36e7dcb92ed1fb085dedd0e2751f901360d1f824` (`docs: authorize M3 business membership`)
Implementation commit: `4cf5db3d503b5e0f299b244e04ce3c0c98f2a40a`
Final evidence commit: this documentation commit (SHA reported in Git history)
PR: `#6 <https://github.com/HEINRICK7/backend-tino/pull/6>` (`feature/m3-business-membership` → `develop`)
M4 authorized: **NO**

This evidence records the implementation and the independent review phase. It
contains no credentials, bearer values, private keys, or generated artifacts.

## Verdict

```text
M3 STATUS: PASS
Architecture: PASS
Build: PASS
Tests: PASS (TEST-M3-001..037, exactly once)
Business: PASS
Membership: PASS
Authorization: PASS
Cross-Tenant Security: PASS
Flyway: PASS
jOOQ: PASS
PostgreSQL: PASS
HTTP Security: PASS
Privacy: PASS
Secret Scan: PASS
Modulith: PASS
Scope Leakage: NONE
M4 AUTHORIZED: NO
```

## Scope and changed files

The feature commit contains only the M3 Business/Membership slice, narrowly
required identity boundary composition, stateless bearer configuration, test
fixture/audit corrections needed to keep M1/M2 green, and the generated-cache
ignore rule. No M4 implementation, functional Device, or unrelated milestone
was added.

Production and schema:

- `app/src/main/resources/db/migration/V2__business_memberships.sql` — additive businesses and business_memberships DDL, constraints, index, and least-privilege runtime grants.
- `app/src/main/java/com/tino/backend/foundation/SecurityFoundationConfiguration.java` — stateless Resource Server CSRF behavior; existing JWT signature/issuer/expiry/sub/client validation remains enabled.
- `modules/business/build.gradle.kts` — Business dependencies and public Identity/Modulith composition wiring.
- `modules/business/src/main/java/com/tino/backend/business/BusinessConfiguration.java`.
- `modules/business/src/main/java/com/tino/backend/business/domain/model/{Business,BusinessMembership,BusinessName,BusinessRole,BusinessStatus,BusinessVertical,MembershipId,MembershipStatus,UserId}.java`.
- `modules/business/src/main/java/com/tino/backend/business/application/model/{AccessibleBusiness,AuthenticatedUser,AuthorizedBusinessContext,CreatedBusiness}.java`.
- `modules/business/src/main/java/com/tino/backend/business/application/exception/{BusinessAccessDeniedException,InactiveAuthenticatedUserException}.java`.
- `modules/business/src/main/java/com/tino/backend/business/application/port/in/AuthenticatedUserResolver.java`.
- `modules/business/src/main/java/com/tino/backend/business/application/port/out/{BusinessMembershipRepository,BusinessPersistenceException,BusinessRepository,DuplicateMembershipException}.java`.
- `modules/business/src/main/java/com/tino/backend/business/application/usecase/{CreateBusiness,ExecuteAuthorizedBusinessOperation,ListUserBusinesses,ResolveBusinessAccess}.java`.
- `modules/business/src/main/java/com/tino/backend/business/adapter/in/identity/BusinessAuthenticatedUserResolver.java`.
- `modules/business/src/main/java/com/tino/backend/business/adapter/in/web/{BusinessApiExceptionHandler,BusinessController}.java`.
- `modules/business/src/main/java/com/tino/backend/business/adapter/out/persistence/{JooqBusinessMembershipRepository,JooqBusinessRepository}.java`.
- `modules/identity/build.gradle.kts` and `modules/identity/src/main/java/com/tino/backend/identity/adapter/in/security/IdentitySecurityConfiguration.java` — public identity resolver composition.
- `modules/identity/src/main/java/com/tino/backend/identity/application/port/in/{AuthenticatedPrincipal,AuthenticatedUserResolver,AuthenticatedUserSnapshot}.java` and named-interface metadata.
- `modules/identity/src/main/java/com/tino/backend/identity/application/exception/package-info.java` — named public error interface.

Tests and audit fixtures:

- `modules/business/src/test/java/com/tino/backend/business/application/BusinessUseCaseTest.java` — framework-free domain/use-case tests (18).
- `app/src/test/java/com/tino/backend/M3BusinessPostgresTest.java` — PostgreSQL 17/Testcontainers persistence, migration, constraint, transaction, and role tests (26).
- `app/src/test/java/com/tino/backend/M3BusinessHttpApiTest.java` — authenticated API and authorization-order tests (9).
- `app/src/test/java/com/tino/backend/M3BusinessBoundaryScopeTest.java` — jOOQ, identity, Modulith, privacy, clean-build, secret-scan, and scope gates (8).
- `app/src/test/java/com/tino/backend/{FoundationPostgresTest,M1ArchitectureTest,M2ArchitecturePrivacyScopeTest,M2IdentityPostgresTest}.java` — dependent M1/M2 fixture and boundary corrections only (truncate dependent M3 rows before users; migration count; adapter-only jOOQ allowance; named-interface metadata exclusion).
- `app/build.gradle.kts` — test-only Spring Security test support.
- `.gitignore` — ignores generated nested `.kotlin/` caches; no cache is tracked.

## Domain, application, and boundaries

`Business` is an authoritative tenant root and is deliberately distinct from
Identity `User`. It contains only `BusinessId`, bounded trimmed `BusinessName`,
the exact vertical set `RETAIL`, `BAKERY`, `RESTAURANT`, `STORE`, `OTHER`,
`BusinessStatus` (`ACTIVE`, `DISABLED`), and `Instant` timestamps.

`BusinessMembership` contains its UUID-v7 `MembershipId`, `BusinessId`, an
opaque internal `UserId`, `BusinessRole` (`OWNER`, `STAFF`),
`MembershipStatus` (`ACTIVE`, `DISABLED`), and timestamps. A user can have
multiple businesses. No User PII, credential, token, `storeId`, Device, or
BusinessProfile data is duplicated.

`CreateBusiness` validates an active authenticated user, generates UUID-v7
Business and Membership ids through the existing M1 `UuidGenerator`, defaults
both records to ACTIVE, and delegates one `createWithOwner` operation. The
jOOQ adapter inserts Business then OWNER Membership in one Spring transaction;
any membership failure raises a port-level exception and rolls the Business
back. PostgreSQL remains the authority for `(business_id,user_id)` uniqueness.

`ListUserBusinesses` reads only active memberships and returns only active
businesses. `ResolveBusinessAccess` checks the requested BusinessId against an
ACTIVE membership first and then an ACTIVE Business, returning an
`AuthorizedBusinessContext` only after both checks. `ExecuteAuthorizedBusinessOperation`
passes that context to `TenantContextExecutor`; it cannot establish tenant
context before authorization.

The domain/application contracts contain no Spring Security, Jwt, jOOQ, JDBC,
SQL exception, Keycloak, or ORM types. jOOQ and Spring annotations are confined
to adapters/composition. Business consumes only the named public Identity
application interface (`AuthenticatedPrincipal`, `AuthenticatedUserResolver`,
and `AuthenticatedUserSnapshot`); it does not reach Identity repositories,
domain internals, or the ResolveAuthenticatedUser implementation.

## Schema and privileges

`V2__business_memberships.sql` is additive; V0 and V1 remain unchanged. It
creates exactly these functional tables:

```text
public.businesses
  id UUID PRIMARY KEY
  trade_name VARCHAR(200) NOT NULL
  vertical VARCHAR(32) NOT NULL
  status VARCHAR(16) NOT NULL
  created_at TIMESTAMPTZ NOT NULL
  updated_at TIMESTAMPTZ NOT NULL

public.business_memberships
  id UUID PRIMARY KEY
  business_id UUID NOT NULL REFERENCES public.businesses(id)
  user_id UUID NOT NULL REFERENCES public.users(id)
  role VARCHAR(16) NOT NULL
  status VARCHAR(16) NOT NULL
  created_at TIMESTAMPTZ NOT NULL
  updated_at TIMESTAMPTZ NOT NULL
```

Checks constrain verticals, business status, membership role, and membership
status to the approved vocabularies. A physical `UNIQUE (business_id,user_id)`
and a user lookup index are present. Foreign keys have no destructive cascade.
Neither table has Business RLS; these are global control-plane authorization
tables. Runtime `tino_app` receives only `SELECT, INSERT` on both tables.
Migration execution remains the separate `tino_migrator` role. No
`owner_user_id`, personal claims, passwords, tokens, `store_id`, `device_id`,
or extra M3+/M4 table is present.

## Security and authorization order

The existing inbound Spring Resource Server remains responsible for JWT
signature, issuer, expiration, required subject, and the documented configured
client contract (`aud` contains `tino-android` OR `azp` equals `tino-android`).
The M3 change disables CSRF for this stateless bearer API, consistent with the
stateless backend baseline; it does not disable authentication or JWT
validation. Only the two M3 endpoints are added: `POST /api/v1/businesses`
and `GET /api/v1/businesses`.

The POST adapter derives the owner from the authenticated subject and rejects a
client-supplied `owner_user_id`; it has no owner or store authority parameter.
The list is scoped through the resolved internal User. A requested business id
or a client `store_id` is never an authorization grant. Cross-business,
missing/disabled membership, and disabled-business requests fail closed with no
cross-tenant body disclosure.

The explicit order probe records:

```text
requested BusinessId
  -> active membership lookup
  -> active business lookup
  -> AuthorizedBusinessContext
  -> TenantContextExecutor / operation
```

The probe observed `membership, business, tenant-context, operation`; denied
requests stopped before tenant context.

## Environment and exact verification

Observed on 2026-08-27:

| Component | Version/evidence |
|---|---|
| Java | OpenJDK 21.0.12 |
| Gradle | 8.14.4 (Kotlin 2.0.21) |
| Spring Boot | 4.1.1 BOM |
| Flyway | 12.4.0 BOM-selected |
| jOOQ runtime | 3.21.7 BOM-selected |
| Testcontainers | 2.0.5 |
| PostgreSQL | `postgres:17-alpine`, server 17.11 |
| Docker | 29.1.3 |

The following commands were run after implementation and during the separate
review phase:

```text
./gradlew clean build architecture migrations --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 13s; 40 actionable tasks; app test XMLs: 72 tests, 0 skipped, 0 failures, 0 errors.

./gradlew :modules:identity:test :modules:business:test --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; identity 8 tests and BusinessUseCaseTest 18 tests passed.

./gradlew :app:test --tests com.tino.backend.M3BusinessPostgresTest --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; 26 tests, skipped=0, failures=0, errors=0.

./gradlew :app:test --tests com.tino.backend.M3BusinessHttpApiTest --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; 9 tests, skipped=0, failures=0, errors=0.

./gradlew :app:test --tests com.tino.backend.M3BusinessBoundaryScopeTest --tests com.tino.backend.M1ArchitectureTest --tests com.tino.backend.M2IdentityPostgresTest --tests com.tino.backend.FoundationPostgresTest --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; selected boundary, M1, M2, and Foundation tests passed.

./gradlew :app:test --tests com.tino.backend.M2SecurityBoundaryTest --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; 8 security boundary tests passed.

JOOQ_JDBC_URL=<runtime-disposable-postgres> JOOQ_JDBC_USER=tino_migrator JOOQ_JDBC_PASSWORD=<runtime-generated> ./gradlew :shared:infrastructure:jooqCodegen --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; jOOQ metadata/code generation completed against PostgreSQL 17.11; generated output remained an ignored build artifact.

./scripts/secret-scan.sh
Secret scan passed.

./scripts/secret-scan.sh --cached
Secret scan passed.

git diff --check
PASS.
```

The PostgreSQL tests use the existing Testcontainers fixture with disposable
runtime-generated role credentials. They migrate an empty database M0→M3,
validate Flyway, run jOOQ as `tino_app`, inspect schema types/constraints, test
atomic rollback, and verify `tino_app`/`tino_migrator` separation. No password
value is recorded here.

Dependency/source/schema/scope audit results:

```text
Runtime dependency audit passed: no forbidden ORM/broker/cache/Keycloak-admin dependency (Hibernate Validator is allowed).
Production dependency/source audit passed.
Business domain/application boundary passed.
Business schema privacy audit passed (business_id is required only on memberships).
Business schema constraint audit passed.
Source/privacy/scope audit passed: framework-free contracts, adapter-only jOOQ, minimal schema fields, and no M3+ markers.
Migration text audit passed: V2 has exact Business/Membership tables, FK/UNIQUE/CHECK/TIMESTAMPTZ/grants, with no users RLS or forbidden fields.
M3 ID matrix audit passed: 37 unique IDs, exactly once; no out-of-range ID.
Generated Kotlin cache ignored and untracked; tracked-cache count=0.
```

The full app JUnit XML totals from the clean review build were:

```text
ApplicationFoundationTest 1
FoundationPostgresTest 1
M1ArchitectureTest 2
M1ConfigurationTest 1
M2ArchitecturePrivacyScopeTest 5
M2IdentityPostgresTest 10
M2SecurityBoundaryTest 8
M3BusinessBoundaryScopeTest 8
M3BusinessHttpApiTest 9
M3BusinessPostgresTest 26
ModularityTest 1
TOTAL 72; skipped=0; failures=0; errors=0
```

## TEST-M3-001..037 matrix

Each required identifier appears exactly once in test sources. Supporting tests
without an identifier add disabled-state, order, and schema detail without
creating duplicate matrix entries.

| ID | Concrete proof | Result |
|---|---|---|
| TEST-M3-001 | `M3BusinessPostgresTest.testM3_001_createBusinessWithActiveDefaults` — real PostgreSQL create, trim, ACTIVE defaults, persistence readback. | PASS |
| TEST-M3-002 | `M3BusinessPostgresTest.testM3_002_creatorBecomesActiveOwner` — OWNER/ACTIVE membership tied to creator. | PASS |
| TEST-M3-003 | `M3BusinessPostgresTest.testM3_003_businessAndOwnerRollbackTogetherWhenOwnerInsertFails` — invalid user FK causes no orphan Business or membership. | PASS |
| TEST-M3-004 | `M3BusinessPostgresTest.testM3_004_listOwnActiveBusinessesOnly` — two owned businesses returned. | PASS |
| TEST-M3-005 | `M3BusinessPostgresTest.testM3_005_listDoesNotExposeForeignBusiness` — foreign business absent from list. | PASS |
| TEST-M3-006 | `M3BusinessPostgresTest.testM3_006_activeMembershipAndBusinessAuthorizeAccess` — AuthorizedBusinessContext produced. | PASS |
| TEST-M3-007 | `M3BusinessPostgresTest.testM3_007_missingMembershipIsDenied` — missing relationship denied. | PASS |
| TEST-M3-008 | `M3BusinessPostgresTest.testM3_008_disabledMembershipIsDenied` — disabled relation denied. | PASS |
| TEST-M3-009 | `M3BusinessPostgresTest.testM3_009_disabledBusinessIsDenied` — disabled tenant denied. | PASS |
| TEST-M3-010 | `M3BusinessPostgresTest.testM3_010_sameUserMayOwnMultipleBusinesses` — no single-business restriction. | PASS |
| TEST-M3-011 | `M3BusinessPostgresTest.testM3_011_membershipUniqueConstraintIsTranslated` — PostgreSQL UNIQUE and port translation. | PASS |
| TEST-M3-012 | `M3BusinessPostgresTest.testM3_012_businessIdentifierIsUuidV7` — Business UUID version 7. | PASS |
| TEST-M3-013 | `M3BusinessPostgresTest.testM3_013_membershipIdentifierIsUuidV7` — Membership UUID version 7. | PASS |
| TEST-M3-014 | `M3BusinessPostgresTest.testM3_014_businessStatusCheckRejectsUnknownValue` — physical status CHECK. | PASS |
| TEST-M3-015 | `M3BusinessPostgresTest.testM3_015_businessVerticalCheckRejectsUnknownValue` — physical vertical CHECK. | PASS |
| TEST-M3-016 | `M3BusinessPostgresTest.testM3_016_membershipRoleCheckRejectsUnknownValue` — physical role CHECK. | PASS |
| TEST-M3-017 | `M3BusinessPostgresTest.testM3_017_membershipStatusCheckRejectsUnknownValue` — physical membership status CHECK. | PASS |
| TEST-M3-018 | `M3BusinessPostgresTest.testM3_018_businessForeignKeyIsPhysical` — nonexistent Business FK rejected. | PASS |
| TEST-M3-019 | `M3BusinessPostgresTest.testM3_019_userForeignKeyIsPhysical` — nonexistent User FK rejected. | PASS |
| TEST-M3-020 | `M3BusinessPostgresTest.testM3_020_flywayMigratesEmptyDatabaseThroughM3` — empty PostgreSQL receives three migrations through V2. | PASS |
| TEST-M3-021 | `M3BusinessPostgresTest.testM3_021_flywayValidatePasses` — Flyway validate. | PASS |
| TEST-M3-022 | `M3BusinessPostgresTest.testM3_022_jooqBusinessAdapterRunsAgainstPostgresql` — repositories and runtime role against PostgreSQL. | PASS |
| TEST-M3-023 | `M3BusinessBoundaryScopeTest.testM3_023_jooqIsConfinedToBusinessPersistenceAdapters` — no jOOQ in contracts; adapter-only use. | PASS |
| TEST-M3-024 | `M3BusinessBoundaryScopeTest.testM3_024_businessModuleDoesNotReachIdentityInternals` — named public Identity boundary only. | PASS |
| TEST-M3-025 | `M3BusinessBoundaryScopeTest.testM3_025_modulithBoundariesVerify` — `ApplicationModules.verify()`. | PASS |
| TEST-M3-026 | `M3BusinessHttpApiTest.testM3_026_crossBusinessRequestedIdIsDeniedWithoutDisclosure` — known foreign id returns 403 with empty body. | PASS |
| TEST-M3-027 | `M3BusinessHttpApiTest.testM3_027_clientBusinessIdIsOnlyARequestedTarget` — id alone cannot grant membership. | PASS |
| TEST-M3-028 | `M3BusinessHttpApiTest.testM3_028_storeIdIsRejectedAndCannotCreateBusinessAuthority` — store id rejected and no row created. | PASS |
| TEST-M3-029 | `M3BusinessBoundaryScopeTest.testM3_029_noDeviceImplementationInBusinessSlice` — no Device/M4 implementation. | PASS |
| TEST-M3-030 | `M3BusinessBoundaryScopeTest.testM3_030_businessSchemaContainsNoUnnecessaryPersonalClaims` — no PII/credential/token fields. | PASS |
| TEST-M3-031 | `M3BusinessHttpApiTest.testM3_031_unauthenticatedCreateReturns401` — unauthenticated POST is 401 without CSRF token. | PASS |
| TEST-M3-032 | `M3BusinessHttpApiTest.testM3_032_authenticatedCreateAssignsOwnerToAuthenticatedUserAndRejectsOwnerUserId` — authenticated creator is OWNER; client owner id is rejected. | PASS |
| TEST-M3-033 | `M3BusinessHttpApiTest.testM3_033_listReturnsOnlyAuthenticatedUsersBusinesses` — GET is membership scoped. | PASS |
| TEST-M3-034 | `M3BusinessPostgresTest.testM3_034_timestampsRoundTripAsInstant` — TIMESTAMPTZ ↔ Instant exact round trip. | PASS |
| TEST-M3-035 | `M3BusinessBoundaryScopeTest.testM3_035_cleanBuildGateUsesRepositoryGradleWrapper` plus the clean build command above. | PASS |
| TEST-M3-036 | `M3BusinessBoundaryScopeTest.testM3_036_secretScanGateIsPresent` plus both secret-scan commands above. | PASS |
| TEST-M3-037 | `M3BusinessBoundaryScopeTest.testM3_037_noOutOfScopeFunctionalMarkersInBusinessProduction` plus scope audits above. | PASS |

## Independent review, deviations, and blockers

Implementation editing stopped before the review phase. The review then
re-inspected the complete production/schema/test diff, reran the clean build,
M1/M2 regressions, M3 PostgreSQL/HTTP/boundary suites, jOOQ generation, source
and dependency audits, secret scans, ID audit, and `git diff --check`.

Three narrowly scoped corrections were required during that review:

1. M3 HTTP adapters were moved from `app.foundation` to the Business inbound
   adapter so Spring Modulith no longer depended on non-exposed Business or
   Identity internals. Named Identity interfaces/errors expose only the
   approved public composition surface; no Identity↔Business cycle remains.
2. M1/M2 PostgreSQL cleanup truncates dependent `business_memberships` and
   `businesses` before `users`, and migration-count expectations now include
   the additive V2 migration. Production M1/M2 behavior is unchanged.
3. The M1 jOOQ audit explicitly permits Business persistence adapters while
   continuing to forbid jOOQ in domain/application contracts. The M3 clean
   build audit checks the repository wrapper rather than generated Gradle
   script text.

CSRF is disabled only in the existing stateless bearer `SecurityFilterChain`,
which is necessary for the required unauthenticated POST 401 boundary and does
not weaken JWT validation. No architecture or specification conflict was
found. No M4 work was started.

The implementation and evidence commits were created after staged-name reviews
and cached secret scans. PR #6 is open against `develop`; its GitHub Actions
gate was queued during this evidence revision and GitGuardian reported pass.
No develop, staging, or main ref is modified by this feature.
Both pre-existing stashes remain preserved and untouched. The generated
`build-logic/.kotlin/` cache is ignored and has zero tracked files.
