# ADR-007 — PostgreSQL Row-Level Security

Status: Accepted

## Context
An omitted tenant predicate must not expose another business.

## Decision
Enable and force RLS on every tenant table from its first migration, using transaction-local `business_id`; also scope queries explicitly.

## Why
Defense-in-depth makes database isolation independent of one application mistake.

## Consequences
The app role cannot bypass RLS; pools/transactions and tests must reproduce real role behavior.

## Rejected alternatives
Application filters alone; schema/database per tenant initially.

## Reconsider when
Only alongside a tenant-storage ADR that provides stronger proven isolation.
