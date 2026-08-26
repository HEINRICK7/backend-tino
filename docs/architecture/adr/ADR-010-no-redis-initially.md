# ADR-010 — No Redis Initially

Status: Accepted

## Context
No measured workload currently needs distributed ephemeral state or caching.

## Decision
Do not add Redis initially.

## Why
PostgreSQL and process-local mechanisms meet known requirements with less operational surface.

## Consequences
No Redis-backed cache, sessions, locks, queues, or rate limits may be introduced implicitly.

## Rejected alternatives
Adding Redis as speculative infrastructure.

## Reconsider when
Measurements show database/cache pressure, distributed rate limiting is required, or ephemeral distributed state has a concrete owner and lifecycle.
