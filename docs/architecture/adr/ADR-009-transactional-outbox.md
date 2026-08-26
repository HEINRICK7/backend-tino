# ADR-009 — Transactional Outbox

Status: Accepted

## Context
Durable side effects cannot rely on in-memory events after database commit.

## Decision
Write outbox entries in PostgreSQL in the same transaction as state; use Spring events only for in-process effects.

## Why
This avoids the dual-write gap without a broker in the initial architecture.

## Consequences
Publishing is at-least-once and consumers must be idempotent; retention/retry need operations.

## Rejected alternatives
Call external APIs in transactions; in-memory-only delivery; broker-first design.

## Reconsider when
Measured throughput or independent consumers justify a broker while preserving transactional publication.
