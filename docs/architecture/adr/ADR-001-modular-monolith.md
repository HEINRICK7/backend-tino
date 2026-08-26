# ADR-001 — Modular Monolith

Status: Accepted

## Context
TINO needs strong domain boundaries without distributed-operations cost at its current scale.

## Decision
Deploy one Spring Boot process whose functional modules have explicit, verified boundaries.

## Why
Atomic transactions and simple operations now, with seams for later extraction if evidence demands it.

## Consequences
One release unit and database; module coupling must be policed in build/tests.

## Rejected alternatives
Microservices (premature coordination/consistency cost); unstructured monolith (boundary decay).

## Reconsider when
A module has independently measured scale, availability, ownership, or release needs that outweigh distributed-system cost.
