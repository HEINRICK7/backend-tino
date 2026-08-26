# ADR-003 — PostgreSQL Shared Database and Schema

Status: Accepted

## Context
TINO needs transactions, constraints, JSON payloads, sequences, and strong tenant defense.

## Decision
Use PostgreSQL with one shared database/schema and `business_id` tenant keys.

## Why
It provides the required correctness primitives, RLS, JSONB, and operational maturity.

## Consequences
Schema/index discipline and PostgreSQL-aware testing are mandatory.

## Rejected alternatives
Database-per-tenant (operational cost); NoSQL primary store (weaker relational invariants).

## Reconsider when
Legal isolation or measured scale makes physical tenant separation necessary.
