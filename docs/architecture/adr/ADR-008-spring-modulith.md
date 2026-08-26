# ADR-008 — Spring Modulith Verification

Status: Accepted

## Context
Package conventions alone do not prevent module erosion.

## Decision
Use Spring Modulith to model and verify functional boundaries in CI.

## Why
Executable architecture turns intended boundaries into a gate.

## Consequences
Public APIs and events must be explicit; violations fail the build.

## Rejected alternatives
Documentation-only boundaries; independent services solely for enforcement.

## Reconsider when
An equivalent or stronger automated boundary verifier replaces it.
