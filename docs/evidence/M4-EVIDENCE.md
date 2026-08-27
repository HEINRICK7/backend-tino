# M4 Evidence — Device Registration & Installation Linking

Recorded: 2026-08-27 (America/Fortaleza)
Repository: `HEINRICK7/backend-tino`
Milestone: `M4 — DEVICE REGISTRATION & INSTALLATION LINKING`
Branch: `feature/m4-device-installation`
Remote develop baseline before authorization: `e5d4b9580c0097b3245104df74582d3d6bb0f166`
Authorization commit: `0a63c5f757967a3e26b683ae1b942bc46d85a62f` (`docs: authorize M4 device installation`)
Implementation commit: `8800ffd4d2652b5d6c2f87f7560753f0261e0971` (`feat: implement M4 device installation foundation`)
Final evidence commit: this documentation commit (SHA reported in Git history)
PR: [#8](https://github.com/HEINRICK7/backend-tino/pull/8), `feature/m4-device-installation` → `develop`
M5 authorized: **NO**

This record covers the implementation phase and the independent Phase B
self-review. It contains no passwords, bearer values, private keys, or
runtime-generated credential values.

## Verdict

```text
M4 STATUS: PASS
Architecture: PASS
Build: PASS
Tests: PASS (TEST-M4-001..043, exactly once)
Device Installation: PASS
Authorization: PASS
Cross-Tenant Security: PASS
RLS: PASS
Concurrency: PASS
Idempotency: PASS
Flyway: PASS
jOOQ: PASS
PostgreSQL: PASS
HTTP Security: PASS
Privacy: PASS
Secret Scan: PASS
Modulith: PASS
Git: PASS at feature handoff
Scope Leakage: NONE
M5 AUTHORIZED: NO
```

## Scope and changed files

The implementation is limited to the authorized M4 installation foundation.
No M5 implementation, migration, endpoint, evidence file, or functional
scope was added. The existing M0–M3 migrations and production behavior remain
unchanged.

Production/schema:

- `app/src/main/resources/db/migration/V3__device_installations.sql` — the
  additive tenant-owned installation table, foreign keys, status check,
  global external-id uniqueness, index, RLS policy, and least-privilege
  runtime grant.
- `modules/device/build.gradle.kts` — narrowly required Business/Identity
  public contracts, Spring web/security/validation, jOOQ, and test launcher
  wiring.
- `modules/device/src/main/java/com/tino/backend/device/DeviceConfiguration.java`.
- `modules/device/src/main/java/com/tino/backend/device/domain/model/{DeviceInstallation,DeviceInstallationId,InstallationExternalId,InstallationStatus}.java`.
- `modules/device/src/main/java/com/tino/backend/device/application/model/ActiveDeviceInstallationContext.java`.
- `modules/device/src/main/java/com/tino/backend/device/application/port/out/DeviceInstallationRepository.java`.
- `modules/device/src/main/java/com/tino/backend/device/application/usecase/{RegisterDeviceInstallation,ResolveDeviceInstallation}.java`.
- `modules/device/src/main/java/com/tino/backend/device/application/exception/*.java` —
  safe access, revoked, unauthenticated, and persistence failures.
- `modules/device/src/main/java/com/tino/backend/device/adapter/out/persistence/JooqDeviceInstallationRepository.java`.
- `modules/device/src/main/java/com/tino/backend/device/adapter/in/web/{DeviceInstallationController,DeviceInstallationApiExceptionHandler}.java`.
- `modules/business/src/main/java/com/tino/backend/business/BusinessConfiguration.java` —
  composition of the narrow public authorization contract.
- `modules/business/src/main/java/com/tino/backend/business/application/port/in/{BusinessAuthorization,BusinessAuthorizationDeniedException,package-info.java}` —
  the named `business-api` contract consumed by Device; no Business repository
  or domain internals cross the module boundary.

Test/audit fixtures:

- `modules/device/src/test/java/com/tino/backend/device/application/DeviceInstallationUseCaseTest.java` —
  11 framework-free unit tests.
- `app/src/test/java/com/tino/backend/M4DevicePostgresTest.java` — 31 real
  PostgreSQL 17/Testcontainers persistence, RLS, role, lifecycle, and
  concurrency tests.
- `app/src/test/java/com/tino/backend/M4DeviceHttpApiTest.java` — 5 Spring
  HTTP/security tests.
- `app/src/test/java/com/tino/backend/M4DeviceBoundaryScopeTest.java` — 9
  boundary, privacy, Modulith, gate, and scope tests.
- `app/src/test/java/com/tino/backend/{FoundationPostgresTest,M1ArchitectureTest,M2IdentityPostgresTest,M3BusinessBoundaryScopeTest,M3BusinessHttpApiTest,M3BusinessPostgresTest}.java` —
  dependent migration-count, jOOQ-boundary, and cleanup-fixture corrections
  required for V3 while preserving M0–M3 behavior.
- `docs/evidence/M4-EVIDENCE.md` — this evidence record.

Generated jOOQ sources and `build-logic/.kotlin/` remain ignored build
outputs; neither is tracked or staged.

## Domain and application

`DeviceInstallation` is a tenant-owned logical installation with server
`DeviceInstallationId` (UUID), explicit `BusinessId`, opaque bounded
`InstallationExternalId`, `InstallationStatus` (`ACTIVE`, `REVOKED`),
provenance-only `registeredByUserId`, and `Instant` timestamps. The value
object trims and rejects blank, oversized, or control-character identifiers.
The existing M1 UUID generator produces UUID v7 server ids.

`RegisterDeviceInstallation` performs Business authorization before entering
the authorized callback. Inside the transaction-local tenant context it finds
the external id, returns an existing ACTIVE row for same-Business
idempotency, rejects REVOKED rows, creates an ACTIVE candidate, and uses the
PostgreSQL unique constraint (`ON CONFLICT DO NOTHING`) to resolve concurrent
first registration. A hidden cross-Business row is returned as safe denial;
there is no JVM, distributed, or database lock.

`ResolveDeviceInstallation` requires the public Business authorization
contract first, then accepts only an ACTIVE installation belonging to that
authorized Business and returns the minimal
`ActiveDeviceInstallationContext`. The installation identifier never grants
Business access and `registeredByUserId` is never used as authority.

The narrow `BusinessAuthorization` named interface delegates to M3's existing
`ResolveBusinessAccess` and `TenantContextExecutor`: ACTIVE authenticated
User/Membership and ACTIVE Business are checked before
`SET LOCAL app.business_id`, and only then does the Device repository run.
Device consumes the public Identity principal/user resolver contract in its
HTTP adapter and does not access Identity repositories, domain types, or
use-case internals.

## Schema, RLS, and privileges

`V3__device_installations.sql` creates exactly:

```text
public.device_installations
  id UUID PRIMARY KEY
  business_id UUID NOT NULL REFERENCES public.businesses(id)
  installation_external_id VARCHAR(200) NOT NULL UNIQUE
  status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE', 'REVOKED'))
  registered_by_user_id UUID NOT NULL REFERENCES public.users(id)
  created_at TIMESTAMPTZ NOT NULL
  updated_at TIMESTAMPTZ NOT NULL
```

Foreign keys use PostgreSQL's default non-destructive behavior; no cascade is
declared. There is no `store_id`, hardware identifier, PII, password, token,
claim, telemetry, or M5 table. No `UNIQUE(business_id)` exists, so a Business
may have multiple installations and a User may retain multiple Businesses.

The table has an index on `business_id`, `ENABLE ROW LEVEL SECURITY`, and
`FORCE ROW LEVEL SECURITY`. Its policy requires the existing transaction-local
`app.business_id` and applies the same predicate to `USING` and `WITH CHECK`.
Without a valid context the runtime role cannot read or insert a row. The
existing role split is preserved: Flyway/DDL runs as `tino_migrator`; runtime
uses `tino_app`, which is `NOSUPERUSER`, `NOBYPASSRLS`, `NOCREATEDB`,
`NOCREATEROLE`, and receives only `SELECT, INSERT` on this table.

## HTTP and security boundary

The only endpoint added is:

```text
POST /api/v1/businesses/{businessId}/installations
```

It requires the existing stateless M2 Resource Server bearer boundary. The
validated JWT is mapped to the framework-independent authenticated principal,
resolved to the internal User, and checked through M3 Membership/Business
authorization before persistence. The request body contains only
`installation_id`; unknown `ownerUserId`, `registeredByUserId`, `userId`, or
`store_id` fields are rejected and never become authority. Responses contain
only internal installation id, opaque external id, Business id, and status.
JWT claims, external subjects, credentials, and tenant session state are not
returned. Unauthenticated requests receive 401; unauthorized Business,
disabled membership/Business, cross-Business collision, and revoked
installation paths receive safe denial responses without resource disclosure.

## Commands and concrete results

All commands below were run from the repository root on Java 21 with the
repository Gradle wrapper. PostgreSQL persistence gates used the existing
Testcontainers fixture (`postgres:17-alpine`) with runtime-generated
disposable role credentials; credential values are intentionally omitted.

```text
./gradlew :app:test --tests com.tino.backend.M1ArchitectureTest \
  --tests com.tino.backend.M2IdentityPostgresTest \
  --tests com.tino.backend.M3BusinessPostgresTest \
  --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; 19 tests; 0 failures/errors/skips

./gradlew :modules:device:test :app:test \
  --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL; app XML reports: 117 tests; device XML report: 11 tests;
0 failures/errors/skips

./gradlew clean build architecture migrations \
  --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 51s; 47 actionable tasks

./gradlew :shared:infrastructure:jooqCodegen \
  --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 28s; 5 actionable tasks; PostgreSQL 17 disposable schema
contained V0–V3 and generated DeviceInstallations metadata; generated output
remained under ignored build/

./scripts/secret-scan.sh
Secret scan passed.

./scripts/secret-scan.sh --cached
Secret scan passed.

git diff --check
PASS (no whitespace errors)
```

The jOOQ command above was executed with `JOOQ_JDBC_URL`,
`JOOQ_JDBC_USER=tino_migrator`, and a runtime-generated
`JOOQ_JDBC_PASSWORD` against a temporary PostgreSQL 17 instance after V0–V3
were applied. No generated source was added to the repository.

Additional Phase B audits:

```text
M4 production dependency/source marker scan: PASS; no ORM, broker, cache,
M5 functional marker, hardware identifier, PII, BYPASSRLS, or credential
candidate found.
M4 source boundary scan: PASS; jOOQ appears only in the Device persistence
adapter; domain/application contain no Spring, Security, JDBC, jOOQ, or
Keycloak types; only public BusinessAuthorization and Identity contracts are
used.
V3 schema scan: PASS; one table, two FKs, ACTIVE/REVOKED CHECK, global UNIQUE,
TIMESTAMPTZ fields, RLS FORCE, and SELECT/INSERT grant.
TEST-M4 id scan: PASS; 43 ids, each TEST-M4-001..043 exactly once, no ids
outside the required range.
Generated artifacts: PASS; build outputs and nested .kotlin caches ignored and
not staged.
```

## TEST-M4-001..043 mapping

| ID | Concrete test method | Result |
|---|---|---|
| 001 | `M4DevicePostgresTest.testM4_001_authorizedUserRegistersInstallation` | PASS |
| 002 | `M4DevicePostgresTest.testM4_002_internalInstallationIdentifierIsUuidV7` | PASS |
| 003 | `M4DevicePostgresTest.testM4_003_newInstallationIsActiveByDefault` | PASS |
| 004 | `M4DevicePostgresTest.testM4_004_businessForeignKeyIsPhysical` | PASS |
| 005 | `M4DevicePostgresTest.testM4_005_registeredUserForeignKeyIsPhysical` | PASS |
| 006 | `M4DevicePostgresTest.testM4_006_statusCheckRejectsUnknownValue` | PASS |
| 007 | `M4DevicePostgresTest.testM4_007_externalInstallationIdIsGloballyUnique` | PASS |
| 008 | `M4DevicePostgresTest.testM4_008_sameBusinessRegistrationIsIdempotent` | PASS |
| 009 | `M4DevicePostgresTest.testM4_009_crossBusinessReassignmentIsDenied` | PASS |
| 010 | `M4DevicePostgresTest.testM4_010_missingMembershipIsDenied` | PASS |
| 011 | `M4DevicePostgresTest.testM4_011_disabledMembershipIsDenied` | PASS |
| 012 | `M4DevicePostgresTest.testM4_012_disabledBusinessIsDenied` | PASS |
| 013 | `M4DevicePostgresTest.testM4_013_clientBusinessIdIsOnlyAnAuthorizedRequestedTarget` | PASS |
| 014 | `M4DevicePostgresTest.testM4_014_installationIdentifierAloneIsNotAuthority` | PASS |
| 015 | `M4DevicePostgresTest.testM4_015_storeIdIsNotPersistedOrAuthority` | PASS |
| 016 | `M4DevicePostgresTest.testM4_016_authorizationPrecedesTenantContextAndPersistence` | PASS |
| 017 | `M4DevicePostgresTest.testM4_017_rlsShowsOwnBusinessInstallation` | PASS |
| 018 | `M4DevicePostgresTest.testM4_018_rlsHidesCrossBusinessInstallation` | PASS |
| 019 | `M4DevicePostgresTest.testM4_019_rlsFailsClosedWithoutTenantContext` | PASS |
| 020 | `M4DevicePostgresTest.testM4_020_tenantContextResetsAfterCommit` | PASS |
| 021 | `M4DevicePostgresTest.testM4_021_tenantContextResetsAfterRollback` | PASS |
| 022 | `M4DevicePostgresTest.testM4_022_concurrentRegistrationProducesOneIdentity` | PASS |
| 023 | `M4DevicePostgresTest.testM4_023_revokedInstallationIsDenied` | PASS |
| 024 | `M4DevicePostgresTest.testM4_024_revokedInstallationIsNeverAutoReactivated` | PASS |
| 025 | `M4DevicePostgresTest.testM4_025_businessMayHaveMultipleInstallations` | PASS |
| 026 | `M4DevicePostgresTest.testM4_026_userCanKeepMultipleBusinesses` | PASS |
| 027 | `M4DeviceBoundaryScopeTest.testM4_027_deviceSchemaHasNoPii` | PASS |
| 028 | `M4DeviceBoundaryScopeTest.testM4_028_noHardwareFingerprintOrSensitiveClientIdentifier` | PASS |
| 029 | `M4DeviceHttpApiTest.testM4_029_registrationRequiresAuthentication` | PASS |
| 030 | `M4DeviceHttpApiTest.testM4_030_crossBusinessRegistrationIsDeniedWithoutDisclosure` | PASS |
| 031 | `M4DeviceHttpApiTest.testM4_031_registrationIsIdempotentOverHttp` | PASS |
| 032 | `M4DevicePostgresTest.testM4_032_timestamptzRoundTripsAsInstant` | PASS |
| 033 | `M4DevicePostgresTest.testM4_033_emptyDatabaseMigratesFromM0ThroughM4` | PASS |
| 034 | `M4DevicePostgresTest.testM4_034_flywayValidatePasses` | PASS |
| 035 | `M4DevicePostgresTest.testM4_035_jooqRepositoryRunsAgainstPostgresql` | PASS |
| 036 | `M4DeviceBoundaryScopeTest.testM4_036_jooqIsConfinedToDevicePersistenceAdapter` | PASS |
| 037 | `M4DeviceBoundaryScopeTest.testM4_037_deviceUsesBusinessPublicAuthorizationOnly` | PASS |
| 038 | `M4DeviceBoundaryScopeTest.testM4_038_deviceUsesIdentityPublicPortOnly` | PASS |
| 039 | `M4DeviceBoundaryScopeTest.testM4_039_modulithBoundariesVerify` | PASS |
| 040 | `M4DevicePostgresTest.testM4_040_runtimeAndMigrationRolesAreLeastPrivileged` | PASS |
| 041 | `M4DeviceBoundaryScopeTest.testM4_041_secretScanGateIsPresentAndExecutable` | PASS |
| 042 | `M4DeviceBoundaryScopeTest.testM4_042_cleanBuildGateUsesRepositoryWrapper` | PASS |
| 043 | `M4DeviceBoundaryScopeTest.testM4_043_noM5OrOutOfScopeFunctionalMarkers` | PASS |

Supplementary M4 unit coverage is in
`DeviceInstallationUseCaseTest` (11 tests) and covers value-object
normalization, active/revoked semantics, idempotency, race winner resolution,
authorization order, and multi-installation/multi-Business behavior.

## Deviations and review decisions

1. A named public `BusinessAuthorization` contract was added because the
   existing M3 `ExecuteAuthorizedBusinessOperation` implementation is an
   internal use case. This keeps Device dependent on Business's public API,
   preserves authorization-before-tenant ordering, and satisfies Spring
   Modulith without exposing repositories or domain internals.
2. M1/M2/M3 PostgreSQL cleanup fixtures now truncate
   `device_installations` before their existing dependent tables. Migration
   counts/current-version expectations were updated from M3 to M4. These are
   test-only compatibility corrections; V0–V2 and runtime behavior were not
   edited.
3. The M1/M3 source audits explicitly allow jOOQ in the new Device persistence
   adapter and allow package-level Modulith `NamedInterface` metadata while
   continuing to reject framework/persistence imports from domain and use-case
   sources.
4. Stateless CSRF behavior was already part of the M2 security baseline and
   was retained; no JWT validation was weakened. No M4 security configuration
   change was necessary.

No architectural or specification conflict was found. M5 remains
unauthorized, and no promotion/merge to `develop`, `staging`, or `main` is
performed by this implementation handoff.
