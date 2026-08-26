# ADR-006 — Keycloak / OAuth2 / OIDC

Status: Accepted

## Context
Android lacks real login and the backend must not implement credential storage.

## Decision
Keycloak authenticates people; Spring Security validates bearer JWTs; `sub` maps to the internal user.

## Why
Standards-based identity separates authentication from business authorization.

## Consequences
Local Keycloak is in Compose; membership still authorizes every tenant operation.

## Rejected alternatives
Custom password/token service; using email as identity; embedding authorization solely in IdP roles.

## Reconsider when
Identity-provider requirements change while OIDC semantics and stable subject mapping remain.
