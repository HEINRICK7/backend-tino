# System Specification — Tenancy

Status: **APPROVED CONTRACT; DATABASE STARTS M1, AUTHORITY M3**

## Authority chain

`validated JWT sub → internal User → active BusinessMembership → Business.id`.

Client business/store IDs are requested targets only. Tenant tables have non-null `business_id` FKs and tenant-leading indexes. RLS is enabled and forced; a transaction-local tenant is established before jOOQ access; queries still use explicit tenant predicates. Pool reuse must not retain tenant state.

## Negative behavior and proof

No context fails closed; Business A cannot access B; a user without membership is forbidden; superuser-only tests are invalid. `TEST-RLS-001..004` inspect policies/roles and prove no-context and A/B isolation for `INV-TENANT-001..003`.
