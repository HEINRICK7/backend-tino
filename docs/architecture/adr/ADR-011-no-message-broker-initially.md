# ADR-011 — No Message Broker Initially

Status: Accepted

## Context
Initial durable asynchronous effects can use a transactional outbox without operating Kafka/RabbitMQ.

## Decision
Do not add Kafka or RabbitMQ initially.

## Why
The current topology does not justify broker complexity.

## Consequences
Outbox publishing starts with a PostgreSQL-backed worker and idempotent consumers.

## Rejected alternatives
Broker-first event architecture; unreliable in-memory delivery for durable effects.

## Reconsider when
Independent consumer teams, fan-out, retention/replay, or measured throughput exceed the PostgreSQL outbox worker's envelope.
