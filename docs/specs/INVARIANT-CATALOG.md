# Permanent Invariant Catalog

| ID | Invariant | First proof | Evidence |
|---|---|---:|---|
| INV-ARCH-001 | Domain/application packages do not depend on infrastructure frameworks. | M0 | Modulith/dependency verification |
| INV-ARCH-002 | A milestone contains no artifact or behavior from a later milestone. | M0 | scope audit |
| INV-DATA-001 | Flyway is the only schema authority. | M0 | config and clean migration test |
| INV-DATA-002 | IDs use UUID v7; cursors use sequences. | M1 | schema/code tests |
| INV-TENANT-001 | Client input cannot establish tenant authority; authority is JWT → User → Membership → Business. | M3 | authorization tests |
| INV-TENANT-002 | Tenant tables combine RLS and explicit `business_id` predicates. | M1/M3 | catalog and integration tests |
| INV-TENANT-003 | App DB role is non-superuser/non-BYPASSRLS. | M1 | role query |
| INV-AUTH-001 | Validated JWT `sub` is the external user identity. | M2 | JWT mapping tests |
| INV-DEVICE-001 | Active membership and linking authorize a device; `storeId` alone never does. | M4 | rejection tests |
| INV-SYNC-001 | `(business_id,event_id)` replay never reapplies effects. | M6 | duplicate test |
| INV-SYNC-002 | Event processing is transactional and leaves no partial state. | M6 | rollback test |
| INV-SYNC-003 | Pull cursor is sequential, deterministic, server-generated, timestamp-independent. | M7 | pagination tests |
| INV-SYNC-004 | Event logic is in registered handlers, never controllers. | M6 | architecture/registry tests |
| INV-OUTBOX-001 | Durable effects and outbox records commit atomically. | M6 | commit/rollback tests |
| INV-FIN-001 | Confirmed ledger entries are never updated/deleted; correction compensates. | M9 | DB/application tests |
| INV-OPS-001 | Compose runs only PostgreSQL/Keycloak; tests use disposable PostgreSQL. | M0 | Compose/Testcontainers evidence |
