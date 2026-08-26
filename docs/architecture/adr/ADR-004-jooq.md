# ADR-004 — jOOQ Persistence

Status: Accepted

## Context
The design relies on explicit SQL, PostgreSQL features, and tenant predicates.

## Decision
Use jOOQ only in persistence adapters; do not expose jOOQ types across ports.

## Why
Type-safe SQL keeps database behavior visible without coupling the domain to persistence.

## Consequences
Adapters own mapping and queries; generated/manual records never cross the boundary.

## Rejected alternatives
JPA/Hibernate as default (implicit SQL and tenancy risk); raw JDBC everywhere (mapping/typing cost).

## Reconsider when
A bounded module demonstrates a better adapter while keeping all invariants and boundaries.
