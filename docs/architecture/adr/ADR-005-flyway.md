# ADR-005 — Flyway as Schema Authority

Status: Accepted

## Context
Reproducible schemas and reviewable changes are required.

## Decision
All schema changes are ordered Flyway migrations; ORM/runtime DDL is disabled.

## Why
An empty database can be deterministically rebuilt and migration history audited.

## Consequences
Every migration needs forward compatibility and integration evidence.

## Rejected alternatives
Automatic ORM DDL; manual production SQL; multiple migration owners.

## Reconsider when
Only if a replacement provides equivalent deterministic, audited migrations.
